# Result I/O and source lookup benchmarks

These opt-in contributor benchmarks use deterministic synthetic data and the
plugin's compiled implementation. They introduce no consumer tasks, dependencies,
cache entries, or report formats. Run from the repository root:

```sh
./gradlew --init-script benchmarks/result-io/benchmark.init.gradle \
  :nebula-archrules-gradle-plugin:benchmarkResultIo --no-configuration-cache
./gradlew --init-script benchmarks/result-io/benchmark.init.gradle \
  :nebula-archrules-gradle-plugin:benchmarkSourceLookup --no-configuration-cache
```

Compile prerequisites finish before measurement in a separate Java 17 process with
a 256 MiB maximum heap. Each fixture has three warmup pairs and ten measured pairs,
alternating baseline/candidate order. Run without other builds/tests. Timings are
informational; correctness checks fail the command if output or source selection
changes. Generated files stay in the plugin module's build directory.

## Result I/O and Markdown

The baseline reproduces the previous unbuffered object-stream file I/O and runs
the current Markdown renderer on an unbuffered file stream. The candidate uses
`ViolationsUtil` and the buffered Markdown file output used by the task. Each pair
checks identical ordered findings and byte-for-byte identical serialized and
Markdown files. Time includes closing/flushing output streams. The combined row
is the median of each pair's summed write/read/Markdown times.

Example observed on 2026-10-29: Java 17.0.14+7-LTS, macOS, aarch64. Milliseconds:

| Findings | Phase | Baseline median [min, max] | Buffered median [min, max] |
| ---: | --- | ---: | ---: |
| 100 | Write results | 0.436 [0.390, 0.508] | 0.278 [0.251, 0.416] |
| 100 | Read results | 0.861 [0.804, 0.948] | 0.213 [0.179, 0.274] |
| 100 | Render Markdown | 0.678 [0.581, 0.882] | 0.378 [0.336, 0.709] |
| 100 | Combined | 2.012 [1.801, 2.228] | 0.866 [0.780, 1.243] |
| 10,000 | Write results | 16.450 [15.646, 17.045] | 3.088 [2.643, 3.330] |
| 10,000 | Read results | 46.211 [45.496, 47.107] | 2.652 [2.354, 3.589] |
| 10,000 | Render Markdown | 15.975 [13.844, 18.653] | 2.846 [1.946, 3.574] |
| 10,000 | Combined | 78.658 [75.069, 82.636] | 8.257 [7.957, 9.831] |

The 10,000-finding fixture produces 1,073,011 serialized bytes and 908,502 Markdown
bytes under both variants. The 100-finding outputs are 12,179 and 10,970 bytes.

## Source lookup

The baseline reproduces scanning every source for each finding, converting matches
to relative paths, and choosing the first match. Its Java stream implementation
does not reproduce every temporary collection allocated by the previous Kotlin
code. The candidate uses the actual `SourceFileIndex`, including its construction
and lazy population in every measured invocation. Fixtures include duplicate
basenames and missing sources; every pair requires identical ordered paths.

The same environment produced these milliseconds:

| Source files | Findings | Scan median [min, max] | Index median [min, max] |
| ---: | ---: | ---: | ---: |
| 101 | 100 | 0.383 [0.362, 1.490] | 0.354 [0.310, 0.826] |
| 1,001 | 10,000 | 168.117 [163.941, 174.544] | 1.406 [1.287, 1.909] |

The shared lookup belongs to one GitHub report execution and serves both console
and JSON annotation generation. It is allocated only when that task executes and
enumerates sources only when a location is needed. Its memory is bounded by that
report's source names and requested locations; it is not persisted across builds.

## Limits and compatibility

These are warmed phase benchmarks with filesystem cache reuse. They do not measure
bytecode import, rule evaluation, a complete report task, cold JVM startup,
remote-cache behavior, or complete consumer command time. Small reports save small
absolute times; source lookup matters only for enabled GitHub reporting. These
numbers cannot establish a CI build speedup.

The regression suite separately checks existing-format reads, byte-identical
writes across buffer boundaries, duplicate source selection, missing-source
fallback, and actual analysis/report execution after configuration-cache reuse on
the supported Gradle matrix. Configuration cache is disabled for these benchmark
commands; benchmark timings themselves are not compatibility evidence.

Validation on 2026-10-29, using only this feature's changes: the complete plugin
suite completed 132 tests with no failures and one existing disabled test; the
core test task was up to date and `validatePlugins` passed. The TestKit matrix
covers Gradle 9.2.0, 9.7.1, and 9.8.2. `ReportPerformanceIntegrationTest` exercises
analysis/Markdown/console/enforcement after configuration-cache reuse with task
caching disabled, owned outputs removed, and findings/report contents checked.
It separately verifies unchanged UP_TO_DATE execution and FROM_CACHE restoration
with identical readable results. `ArchrulesRunnerPluginGithubTest` checks GitHub
report execution and identical JSON after configuration-cache reuse. Existing JSON
and aggregate report suites passed in the full run. The variant-resolution fixture
uses Kotlin Gradle plugin 2.4.21 to retain its no-deprecation assertion on Gradle 9.8.2.

```sh
./gradlew :nebula-archrules-core:test :nebula-archrules-gradle-plugin:test \
  :nebula-archrules-gradle-plugin:validatePlugins
```
