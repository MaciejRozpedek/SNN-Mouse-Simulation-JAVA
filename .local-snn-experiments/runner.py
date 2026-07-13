#!/usr/bin/env python3
"""Generate, run and compare local SNN benchmark experiments."""

from __future__ import annotations

import argparse
import csv
import json
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any

import yaml


HERE = Path(__file__).resolve().parent
REPO = HERE.parent
DEFAULT_MANIFEST = HERE / "experiments.yaml"
PATH_TOKEN = re.compile(r"([^.\[\]]+)|\[(\d+)]")


def load_yaml(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        result = yaml.safe_load(handle)
    if not isinstance(result, dict):
        raise ValueError(f"Expected a YAML mapping in {path}")
    return result


def resolve_from(base: Path, value: str) -> Path:
    path = Path(value)
    return path.resolve() if path.is_absolute() else (base / path).resolve()


def parse_path(path: str) -> list[str | int]:
    tokens: list[str | int] = []
    position = 0
    for match in PATH_TOKEN.finditer(path):
        separator = path[position:match.start()]
        if separator not in ("", "."):
            raise ValueError(f"Invalid override path: {path}")
        tokens.append(match.group(1) if match.group(1) is not None else int(match.group(2)))
        position = match.end()
    if not tokens or position != len(path):
        raise ValueError(f"Invalid override path: {path}")
    return tokens


def apply_override(document: Any, path: str, value: Any) -> None:
    tokens = parse_path(path)
    target = document
    for token in tokens[:-1]:
        if isinstance(token, int):
            if not isinstance(target, list) or token >= len(target):
                raise KeyError(f"List index {token} does not exist in {path}")
        elif not isinstance(target, dict) or token not in target:
            raise KeyError(f"Key {token!r} does not exist in {path}")
        target = target[token]

    final = tokens[-1]
    if isinstance(final, int):
        if not isinstance(target, list) or final >= len(target):
            raise KeyError(f"List index {final} does not exist in {path}")
    elif not isinstance(target, dict) or final not in target:
        raise KeyError(f"Key {final!r} does not exist in {path}")
    target[final] = value


def generate_candidate(source: Path, overrides: list[dict[str, Any]], output: Path) -> None:
    document = load_yaml(source)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not overrides:
        shutil.copyfile(source, output)
        return
    for override in overrides:
        if set(override) != {"path", "value"}:
            raise ValueError(f"Override must contain exactly path and value: {override}")
        apply_override(document, str(override["path"]), override["value"])
    with output.open("w", encoding="utf-8", newline="\n") as handle:
        yaml.safe_dump(document, handle, sort_keys=False, allow_unicode=True)


def candidate_source(manifest_path: Path, manifest: dict[str, Any], experiment: dict[str, Any]) -> tuple[Path, list[dict[str, Any]]]:
    has_config_file = "config_file" in experiment
    has_overrides = "overrides" in experiment
    config_file = experiment.get("config_file")
    overrides = experiment.get("overrides") or []
    if has_config_file and has_overrides:
        raise ValueError(f"Experiment {experiment.get('name')!r} cannot use both config_file and overrides")
    if has_config_file:
        if not isinstance(config_file, str) or not config_file.strip():
            raise ValueError(f"Experiment {experiment.get('name')!r} has an empty config_file")
        return resolve_from(manifest_path.parent, str(config_file)), []
    if "base_config" not in manifest:
        raise ValueError(f"Experiment {experiment.get('name')!r} needs config_file or manifest base_config")
    return resolve_from(manifest_path.parent, str(manifest["base_config"])), overrides


def post_json(url: str, params: dict[str, Any], timeout: float) -> dict[str, Any]:
    query = urllib.parse.urlencode(params)
    request = urllib.request.Request(f"{url}?{query}", method="POST", data=b"")
    with urllib.request.urlopen(request, timeout=timeout) as response:
        if response.status >= 300:
            raise RuntimeError(f"POST {url} returned HTTP {response.status}")
        result = json.load(response)
    if not isinstance(result, dict) or not {"parameters", "summary", "runs"}.issubset(result):
        raise ValueError(f"Benchmark response has an unexpected shape: {result!r}")
    return result


def wait_for_app(url: str, process: subprocess.Popen[Any], timeout: float) -> None:
    deadline = time.monotonic() + timeout
    last_error: Exception | None = None
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError(f"Application exited during startup: {process.returncode}")
        try:
            with urllib.request.urlopen(url, timeout=1.0) as response:
                if response.status < 500:
                    return
        except (urllib.error.URLError, TimeoutError) as error:
            last_error = error
        time.sleep(0.2)
    raise TimeoutError(f"Application startup timed out: {last_error}")


def run_once(jar: Path, candidate: Path, run_dir: Path, settings: dict[str, Any]) -> dict[str, Any]:
    port = int(settings.get("port", 18080))
    base_url = f"http://127.0.0.1:{port}"
    run_dir.mkdir(parents=True, exist_ok=True)
    command = [
        "java", "-jar", str(jar), f"--server.port={port}",
        f"--snn.config.path={candidate}", "--spring.main.banner-mode=off",
    ]
    benchmark_params = {
        "durationMs": settings.get("duration_ms", 10_000),
        "stepMs": settings.get("step_ms", 1),
        "burnInMs": settings.get("burn_in_ms", 1_000),
        "repeats": settings.get("repeats", 10),
        "baseSeed": settings.get("base_seed", 1),
        "learningEnabled": settings.get("learning_enabled", True),
    }
    stdout_path = run_dir / "application.stdout.log"
    stderr_path = run_dir / "application.stderr.log"
    with stdout_path.open("w", encoding="utf-8") as stdout, stderr_path.open("w", encoding="utf-8") as stderr:
        process = subprocess.Popen(command, cwd=REPO, stdout=stdout, stderr=stderr)
        try:
            wait_for_app(base_url + "/", process, float(settings.get("startup_timeout_seconds", 30)))
            benchmark = post_json(
                base_url + "/api/benchmark",
                benchmark_params,
                float(settings.get("benchmark_timeout_seconds", 600)),
            )
            write_json(run_dir / "benchmark.json", benchmark)
            return benchmark
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=5.0)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5.0)


def write_json(path: Path, value: Any) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def aggregate(results: list[dict[str, Any]], baseline_name: str, settings: dict[str, Any]) -> list[dict[str, Any]]:
    report: list[dict[str, Any]] = []
    low_hz = float(settings.get("firing_rate_min_hz", 0.1))
    high_hz = float(settings.get("firing_rate_max_hz", 50.0))
    minimum_stable_ratio = float(settings.get("minimum_stable_run_ratio", 0.8))
    minimum_repeats = int(settings.get("minimum_repeats", 10))
    for result in results:
        summary = result["benchmark"]["summary"]
        firing = float(summary["meanFinalFiringRateHz"])
        run_firing = [float(run["finalFiringRateHz"]) for run in result["benchmark"]["runs"]]
        stable_run_ratio = sum(low_hz <= value <= high_hz for value in run_firing) / len(run_firing)
        report.append({
            "experiment": result["experiment"],
            "repeats": int(result["benchmark"]["parameters"]["repeats"]),
            "learningEnabled": bool(result["benchmark"]["parameters"].get("learningEnabled", True)),
            "meanEvaluationRewards": float(summary["meanEvaluationRewards"]),
            "rewardStandardDeviation": float(summary["rewardStandardDeviation"]),
            "meanRewardTrend": float(summary["meanRewardTrend"]),
            "meanFinalFiringRateHz": firing,
            "meanWeightDelta": float(summary["meanWeightDelta"]),
            "meanPathLength": float(summary["meanPathLength"]),
            "simulatedToWallRatio": float(summary["simulatedToWallRatio"]),
            "stableRunRatio": stable_run_ratio,
            "firingStable": stable_run_ratio >= minimum_stable_ratio,
        })
    baseline = next((row for row in report if row["experiment"] == baseline_name), None)
    if baseline is None:
        raise ValueError(f"Baseline experiment is missing from this batch: {baseline_name}")
    for row in report:
        row["deltaRewardsVsBaseline"] = row["meanEvaluationRewards"] - baseline["meanEvaluationRewards"]
        if row["experiment"] == baseline_name:
            row["verdict"] = "reference"
        elif row["repeats"] < minimum_repeats:
            row["verdict"] = "insufficient_repeats"
        elif not row["firingStable"]:
            row["verdict"] = "unstable_firing"
        elif row["deltaRewardsVsBaseline"] <= 0:
            row["verdict"] = "no_reward_gain"
        elif row["meanRewardTrend"] <= baseline["meanRewardTrend"]:
            row["verdict"] = "no_learning_trend"
        else:
            row["verdict"] = "promising"
    return sorted(report, key=lambda row: (row["deltaRewardsVsBaseline"], row["meanRewardTrend"]), reverse=True)


def write_report(batch: Path, rows: list[dict[str, Any]], settings: dict[str, Any]) -> None:
    fields = [
        "experiment", "learningEnabled", "repeats", "meanEvaluationRewards", "rewardStandardDeviation",
        "meanRewardTrend", "meanFinalFiringRateHz", "meanWeightDelta", "meanPathLength",
        "simulatedToWallRatio", "deltaRewardsVsBaseline", "stableRunRatio", "firingStable", "verdict",
    ]
    with (batch / "report.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    low_hz = settings.get("firing_rate_min_hz", 0.1)
    high_hz = settings.get("firing_rate_max_hz", 50.0)
    lines = [
        "# SNN benchmark report", "",
        f"Stable firing range: {low_hz}-{high_hz} Hz. Reward delta is relative to the baseline. The second table column is learningEnabled.", "",
        "| Experiment | learningEnabled | Repeats | Mean rewards | Reward SD | Trend | Final Hz | Stable runs | Weight delta | Path | Sim/wall | Reward delta | Verdict |",
        "|---|:---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for row in rows:
        lines.append(
            f"| {row['experiment']} | {str(row['learningEnabled']).lower()} | {row['repeats']} | {row['meanEvaluationRewards']:.3f} | "
            f"{row['rewardStandardDeviation']:.3f} | {row['meanRewardTrend']:.3f} | "
            f"{row['meanFinalFiringRateHz']:.3f} | {row['stableRunRatio']:.2f} | {row['meanWeightDelta']:.6f} | "
            f"{row['meanPathLength']:.3f} | {row['simulatedToWallRatio']:.3f} | "
            f"{row['deltaRewardsVsBaseline']:.3f} | {row['verdict']} |"
        )
    (batch / "report.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def execute(manifest_path: Path, selected: set[str] | None) -> Path:
    manifest = load_yaml(manifest_path)
    jar = resolve_from(manifest_path.parent, str(manifest["jar"]))
    if not jar.exists():
        raise FileNotFoundError(f"Build the application first; JAR not found: {jar}")
    settings = manifest.get("settings") or {}
    batch = HERE / "results" / datetime.now().strftime("%Y%m%d-%H%M%S")
    batch.mkdir(parents=True, exist_ok=False)
    write_json(batch / "manifest.snapshot.json", manifest)
    results: list[dict[str, Any]] = []
    for experiment in manifest.get("experiments") or []:
        name = str(experiment["name"])
        if selected is not None and name not in selected:
            continue
        if not bool(experiment.get("enabled", True)):
            continue
        experiment_settings = experiment.get("benchmark_settings") or {}
        if not isinstance(experiment_settings, dict):
            raise ValueError(f"Experiment {name!r} benchmark_settings must be a mapping")
        effective_settings = dict(settings)
        effective_settings.update(experiment_settings)
        source, overrides = candidate_source(manifest_path, manifest, experiment)
        if not source.exists():
            raise FileNotFoundError(f"Config file not found for {name}: {source}")
        run_dir = batch / name / "run-01"
        candidate = run_dir / "candidate.yaml"
        generate_candidate(source, overrides, candidate)
        metadata = {
            "experiment": name, "configFile": str(source), "overrides": overrides,
            "startedAt": datetime.now().astimezone().isoformat(),
            "benchmarkSettings": experiment_settings,
        }
        try:
            benchmark = run_once(jar, candidate, run_dir, effective_settings)
            result = metadata | {"status": "completed", "benchmark": benchmark}
        except Exception as error:
            result = metadata | {"status": "failed", "error": repr(error)}
            write_json(run_dir / "summary.json", result)
            raise
        write_json(run_dir / "summary.json", result)
        results.append(result)
        summary = benchmark["summary"]
        print(f"{name}: rewards={summary['meanEvaluationRewards']} trend={summary['meanRewardTrend']}", flush=True)
    if not results:
        raise RuntimeError("No enabled experiments matched the selection")
    rows = aggregate(results, str(manifest.get("baseline_experiment", "baseline")), settings)
    write_json(batch / "report.json", rows)
    write_report(batch, rows, settings)
    return batch


def generate_only(manifest_path: Path, selected: set[str] | None) -> None:
    manifest = load_yaml(manifest_path)
    output = HERE / "generated"
    count = 0
    for experiment in manifest.get("experiments") or []:
        name = str(experiment["name"])
        if selected is not None and name not in selected:
            continue
        source, overrides = candidate_source(manifest_path, manifest, experiment)
        if not source.exists():
            raise FileNotFoundError(f"Config file not found for {name}: {source}")
        generate_candidate(source, overrides, output / f"{name}.yaml")
        count += 1
    print(f"Generated {count} candidate(s) in {output}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--only", action="append", help="Experiment name; may be repeated")
    parser.add_argument("--generate-only", action="store_true")
    args = parser.parse_args()
    selected = set(args.only) if args.only else None
    manifest = args.manifest.resolve()
    if args.generate_only:
        generate_only(manifest, selected)
    else:
        batch = execute(manifest, selected)
        print(f"Report: {batch / 'report.md'}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("Interrupted", file=sys.stderr)
        raise SystemExit(130)
