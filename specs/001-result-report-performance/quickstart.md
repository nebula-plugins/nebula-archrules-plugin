# Verify result I/O and reporting

Run commands from the repository root using the existing Gradle wrapper and
configured Java toolchains.

## Correctness and Gradle compatibility

```sh
./gradlew :nebula-archrules-core:test :nebula-archrules-gradle-plugin:test \
  :nebula-archrules-gradle-plugin:validatePlugins
```

`ViolationsUtilTest` checks serialization compatibility. The source-index printer
tests check selection and lazy enumeration. `ReportPerformanceIntegrationTest`
executes analysis, Markdown, console, and enforcement after configuration-cache
reuse with problems treated as failures and task caching disabled; it checks
UP_TO_DATE and FROM_CACHE separately. `ArchrulesRunnerPluginGithubTest` executes
GitHub reporting after reuse. Supported Gradle versions come from
`SupportedGradleVersions.kt`.

Verified on 2026-10-29 with Gradle 9.2.0, 9.7.1, and 9.8.2. The full plugin
suite completed 132 tests with no failures and one existing disabled test.

## Performance

```sh
./gradlew --init-script benchmarks/result-io/benchmark.init.gradle \
  :nebula-archrules-gradle-plugin:benchmarkResultIo --no-configuration-cache
./gradlew --init-script benchmarks/result-io/benchmark.init.gradle \
  :nebula-archrules-gradle-plugin:benchmarkSourceLookup --no-configuration-cache
```

Run these commands without concurrent builds/tests. Each benchmark warms up both
variants, alternates ten measured pairs, and checks output parity. Configuration
cache is disabled for the benchmark commands; these timings are separate from the
compatibility tests. Read the [fixture details and limits](../../benchmarks/result-io/README.md)
before interpreting the numbers as consumer-build savings.

## Specification tooling

```sh
SPECIFY_FEATURE_DIRECTORY=specs/001-result-report-performance \
  .specify/scripts/bash/check-prerequisites.sh \
  --json --require-spec --require-tasks --include-tasks
```
