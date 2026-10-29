# Feature: faster result I/O and report generation

**Status**: Implemented and verified on the scoped change set, 2026-10-29.

**Feature ID**: `001-result-report-performance`

## Consumer problem and scope

Analysis writes serialized findings through an unbuffered file stream. Reports
and enforcement read those files through another unbuffered stream, and Markdown
output writes each fragment directly to a file. GitHub annotations scan all source
files for each finding, independently for console and JSON output.

Reduce these costs when the corresponding actions execute. Changes cover result
writing/reading, Markdown file output, and GitHub annotation source lookup. Existing
analysis semantics, rule evaluation, report formats, and task contracts are retained.

## User stories

### US1: cheaper result I/O

As a consumer with many findings, I want analysis result writes and subsequent
report reads to avoid repeated small file operations while retaining compatible
results and reports.

Acceptance: existing serialized files remain readable; newly written files are
byte-identical to the previous format, including data crossing buffer boundaries.
Markdown bytes, finding order, priorities, and PASS/FAIL/NO_MATCH status are unchanged.

### US2: source lookup proportional to report size

As a consumer enabling GitHub annotations, I want source lookup to enumerate the
source collection once per report execution and reuse resolved paths for both
console and JSON annotations.

Acceptance: preserve the first matching source for duplicate basenames and the
filename fallback when no source matches. Reports containing only passing results
must not enumerate sources. Lookup state must end with the report execution.

### US3: compatible Gradle execution

As a consumer using configuration cache and task-output caching, I want these
improvements without new configuration, cache artifacts, or output changes.

Acceptance: affected actions execute successfully after configuration-cache reuse
with problems treated as failures on the supported Gradle matrix. Check actual
execution after removing owned outputs and disabling build-cache restoration.
Separately retain UP_TO_DATE and FROM_CACHE behavior with readable, identical data.

## Requirements

- FR-001: Buffer result-file input/output and Markdown file output with bounded
  standard buffers; close streams within each operation.
- FR-002: Preserve serialization, record ordering, existing error behavior,
  effective priorities, report thresholds, and annotation fields.
- FR-003: Share one lazily populated, execution-local source index across both
  GitHub annotation paths; preserve source iteration order and fallback behavior.
- FR-004: Preserve existing task inputs/outputs, cache annotations, public DSL,
  constructor/helper entry points, and worker isolation. Introduce no eager
  configuration-time resolution or execution-time Project access.
- FR-005: Record public reproducible performance measurements with correctness
  checks, warmup, alternating comparisons, distributions, and material limitations.

## Success criteria and limits

Repeated matched phase benchmarks must show useful savings for larger fixtures
while preserving output bytes and selected source paths. Keep index construction
and stream closing in their measured intervals. Check smaller fixtures as well.

These measurements establish result-handling and source-lookup savings. They do
not establish faster bytecode import, rule evaluation, cold startup, or whole-build
wall time. Source-lookup savings apply when GitHub reporting is enabled. Public
commands, fixtures, and observed distributions live in
[benchmarks/result-io](../../benchmarks/result-io/README.md).
