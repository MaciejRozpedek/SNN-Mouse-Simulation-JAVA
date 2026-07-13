#!/usr/bin/env python3
"""Paired comparison of two benchmark.json files using their shared seeds."""

from __future__ import annotations

import argparse
import itertools
import json
import math
import random
import statistics
from pathlib import Path


def percentile(sorted_values: list[float], probability: float) -> float:
    position = probability * (len(sorted_values) - 1)
    lower = math.floor(position)
    upper = math.ceil(position)
    if lower == upper:
        return sorted_values[lower]
    fraction = position - lower
    return sorted_values[lower] * (1.0 - fraction) + sorted_values[upper] * fraction


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--metric", default="evaluationRewards")
    parser.add_argument("--bootstrap-samples", type=int, default=100_000)
    args = parser.parse_args()

    reference = json.loads(args.reference.read_text(encoding="utf-8"))
    candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
    reference_runs = reference["runs"]
    candidate_runs = candidate["runs"]
    reference_seeds = [run["seed"] for run in reference_runs]
    candidate_seeds = [run["seed"] for run in candidate_runs]
    if reference_seeds != candidate_seeds:
        raise ValueError("Benchmark seed sequences do not match")

    differences = [
        float(candidate_run[args.metric]) - float(reference_run[args.metric])
        for reference_run, candidate_run in zip(reference_runs, candidate_runs, strict=True)
    ]
    observed_mean = statistics.fmean(differences)
    median = statistics.median(differences)
    stdev = statistics.stdev(differences) if len(differences) > 1 else 0.0
    effect_dz = observed_mean / stdev if stdev > 0 else math.inf

    rng = random.Random(104729)
    bootstrap_means = sorted(
        statistics.fmean(rng.choice(differences) for _ in differences)
        for _ in range(args.bootstrap_samples)
    )
    bootstrap_ci = [percentile(bootstrap_means, 0.025), percentile(bootstrap_means, 0.975)]

    if len(differences) <= 20:
        permutations = [
            statistics.fmean(sign * value for sign, value in zip(signs, differences, strict=True))
            for signs in itertools.product((-1.0, 1.0), repeat=len(differences))
        ]
        tolerance = 1e-12
        two_sided_p = sum(abs(value) + tolerance >= abs(observed_mean) for value in permutations) / len(permutations)
        one_sided_p = sum(value + tolerance >= observed_mean for value in permutations) / len(permutations)
    else:
        two_sided_p = None
        one_sided_p = None

    reference_mean = statistics.fmean(float(run[args.metric]) for run in reference_runs)
    result = {
        "metric": args.metric,
        "seeds": reference_seeds,
        "differencesCandidateMinusReference": differences,
        "referenceMean": reference_mean,
        "candidateMean": statistics.fmean(float(run[args.metric]) for run in candidate_runs),
        "meanDifference": observed_mean,
        "medianDifference": median,
        "relativeDifference": observed_mean / reference_mean if reference_mean else None,
        "positiveEqualNegative": [
            sum(value > 0 for value in differences),
            sum(value == 0 for value in differences),
            sum(value < 0 for value in differences),
        ],
        "sampleStdevDifference": stdev,
        "pairedEffectDz": effect_dz,
        "bootstrap95CiMeanDifference": bootstrap_ci,
        "exactSignFlipTwoSidedP": two_sided_p,
        "exactSignFlipOneSidedP": one_sided_p,
    }
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
