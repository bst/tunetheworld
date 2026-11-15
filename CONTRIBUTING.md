# Contributing

Thanks for helping with Tune the World. Bug reports, ideas and pull requests are all welcome. For a larger change, open an issue first so we can agree on the approach before you put in the work.

## Pull requests

[Building](README.md#building) in the README explains how to build the app, run the checks and enable the git hooks. Before opening a pull request:

- Enable the git hooks, or run ktlint, lint and the unit tests yourself. All of them must pass.
- Fix new lint warnings instead of adding them to `app/lint-baseline.xml`.
- Follow the project's [constraints](README.md#constraints).
- Add every new string in English and German. If you don't speak German, say so in the pull request and the translation will be added.

## Changing the pitch detection

[PITCH_DETECTION.md](PITCH_DETECTION.md) explains how the detector works and why. [Tests](README.md#tests) in the README describes how it is checked against real recordings.

- `SampleValidationTest` must still pass. It prints a table for every recording; compare it with the output before your change.
- If your change fixes a recording marked `known-failure` in `samples.tsv`, remove the mark.
- For changes that affect many readings, compare the old and new versions block by block with `EvaluationExport` and [docs/research/evaluate.py](docs/research/evaluate.py), and add the result to the [research log](docs/research/RESEARCH_LOG.md), including ideas that did not work.
- Update PITCH_DETECTION.md and the README if what they describe changes.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE), the same license as the project.
