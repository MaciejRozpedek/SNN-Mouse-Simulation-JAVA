# SNN learning work plan

- [ ] **Worker-review development loop** - use a faster implementation worker, then main-agent review and tests before each commit.
- [ ] **Continuous mathematical analysis** - keep Meitner, Hooke and Poincare analysing in parallel on ultra reasoning. Their primary task is to explore which network configurations are worth testing next, explain mathematically and qualitatively how to improve them, cross-review one another through the shared files, and write separate `network-analysis-{name}.md` reports.
- [ ] **Mathematical configuration templates** - each mathematical agent writes every complete, benchmark-ready proposal to a separate `templates-{name}/NNN-short-description.yaml` file. The opening YAML comments must explain the hypothesis and motivation, mathematical mechanism, difference from the baseline, expected outcome, metrics and rejection criterion.
- [x] **Project and configuration review** - inspect the PDF, YAML schema and extension points.
- [x] **Background and hunger inputs** - add tonic noise and delayed, meal-reset hunger drive.
- [x] **Benchmark design** - profile the current loop and add a dedicated headless endpoint.
- [ ] **Additional strategies** - evaluate concrete sensory and motor strategies useful for learning.
- [x] **Directional motor outputs** - add separate forward, left and right output populations.
- [x] **Experiment harness** - use the headless endpoint for repeatable candidate generation, execution and result analysis.
- [ ] **Configuration search** - test topology, weights, inputs and outputs in staged sweeps.
- [ ] **Repeated validation** - run promising configurations at least ten times.
- [ ] **Learning evidence** - demonstrate repeatable improvement in food-seeking behaviour.
