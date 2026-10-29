# Feature specifications

This repository uses [GitHub Spec Kit](https://github.com/github/spec-kit) for
focused feature specifications, implementation plans, and verification records.
The shared setup uses Specify CLI **1.2.5**, shell helpers, and the **none** agent
integration. Contributor tooling is separate from the Gradle consumer plugins.

| Feature | Documents |
| --- | --- |
| Result I/O and report performance | [Spec](001-result-report-performance/spec.md), [plan](001-result-report-performance/plan.md), [tasks](001-result-report-performance/tasks.md), [validation](001-result-report-performance/quickstart.md) |

## Setup and use

Install `specify-cli==1.2.5` using the
[upstream installation guide](https://github.github.io/spec-kit/installation.html).
The version should match `.specify/init-options.json`. Initialization is already
checked in; do not rerun it to resume an existing feature.

```sh
specify --version
specify integration status
SPECIFY_FEATURE_DIRECTORY=specs/001-result-report-performance \
  .specify/scripts/bash/check-prerequisites.sh \
  --json --require-spec --require-tasks --include-tasks
```

The shared `none` integration provides templates and helpers without choosing an
agent or installing agent commands. To use native agent commands, follow the
[integration guide](https://github.github.io/spec-kit/integrations/index.html)
and select an integration locally. Restore `none` before sharing generated changes.

## Feature workflow

Read the [project constitution](../.specify/memory/constitution.md), then keep each
feature in `specs/<number>-<name>/`:

- `spec.md`: consumer problem, requirements, boundaries, and acceptance criteria.
- `plan.md`: implementation choices, compatibility, and verification strategy.
- `tasks.md`: concrete implementation and validation work, checked off only after completion.
- `quickstart.md`: reproducible verification commands when useful.

Keep requirements, implementation, and evidence consistent. Use public fixtures
and references. Do not include private project details, internal issue identifiers,
or machine-specific paths. Per-checkout feature state in `.specify/feature.json`
is ignored. Preserve upstream-generated scripts and templates; project requirements
belong in the constitution and feature documents.
