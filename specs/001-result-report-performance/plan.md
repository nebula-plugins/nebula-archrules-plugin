# Implementation plan: result I/O and report performance

## Design

Wrap file streams in standard buffers before constructing ObjectInputStream or
ObjectOutputStream in `ViolationsUtil`. Buffer the Markdown task's output stream
and close it within the task action. This reduces file operations while retaining
the exact serialized and rendered bytes; file ownership and the result format stay
the same.

Introduce an internal `SourceFileIndex` owned by a single `GithubReportTask`
execution. Lazily group sources by basename, retaining iteration order, and resolve
requested paths once. Share the index between console and JSON annotation
generation. Keep the existing three-argument printer constructor and standalone
annotation helper callable; the standalone helper retains its original direct
lookup behavior. Do not store the index on a task, build service, or daemon-global
object, or send it across the worker serialization boundary.

## Compatibility and cost

No consumer settings, task properties, dependencies, or persisted cache artifacts
are added. Buffers require small bounded memory per open stream. The source index
uses memory proportional to source entries and requested location names, and
enumerates sources only when an annotation needs a path. Classloader isolation,
threshold policy, result serialization, and Java toolchain conventions are retained.

## Verification

1. Test byte-identical serialization across buffer boundaries, legacy reads, and
   reads of new output with an ordinary ObjectInputStream, including Unicode and
   all result statuses.
2. Test duplicate source selection, repeated locations, missing-source fallback,
   console/JSON parity, and no source enumeration for passing-only reports.
3. Run a self-contained active-rule consumer after configuration-cache storage
   and reuse on `SupportedGradleVersions.kt` entries. Remove only owned outputs;
   use `--no-build-cache` to prove analysis and reports execute after reuse.
   Check unchanged UP_TO_DATE and populated-cache FROM_CACHE separately.
4. Exercise GitHub report actions after configuration-cache reuse and require
   identical JSON output on the supported matrix.
   Include Gradle 9.8.2 in the matrix and use Kotlin Gradle plugin 2.4.21 in the
   variant-resolution fixture so the no-deprecation assertion remains enabled.
5. Run the core/plugin suites and `validatePlugins` on this isolated change set.
6. Run opt-in public benchmarks without concurrent builds/tests. Compare previous
   unbuffered I/O and repeated scans with the compiled implementation over ten
   alternating measured pairs after warmup. Validate every pair's output parity.

See [quickstart.md](quickstart.md) for commands and
[benchmark evidence](../../benchmarks/result-io/README.md) for distributions and
measurement limits. Spec Kit scaffolding is contributor-only and is not applied
to consumer builds.
