# Nebula ArchRules Constitution

## Core Principles

### I. Preserve consumer contracts

Treat published task types, task names, extension properties, plugin IDs, rule APIs,
and result formats as consumer contracts. Changes must preserve findings,
PASS/FAIL/NO_MATCH meaning, effective priorities, enforcement, and reporting unless
the feature explicitly designs and documents a compatible migration. Performance
work must preserve the information rules need, including dependency relationships,
annotations, package metadata, and source locations.

### II. Support configuration cache and lazy configuration

Wire tasks and inputs through Gradle providers and declared properties. Do not
introduce eager dependency resolution during plugin application or capture Project,
Configuration, task instances, or extensions in execution state. Worker-local
objects belong inside worker execution. Changes affecting task or worker state
must validate configuration-cache storage and reuse with problems treated as
failures. Exercise the changed action after reuse when testing execution logic.

### III. Keep task caching correct

Model output-affecting inputs and owned outputs accurately. Do not weaken input
normalization, omit rule-relevant bytes, or hide inputs with @Internal to increase
cache hits. Preserve correct up-to-date and build-cache behavior, including stale
output transitions. Runtime timings and diagnostic state must not enter cacheable
finding files. Reuse of imported models requires a defined lifetime and correct
invalidation; do not introduce an unbounded global cache.

### IV. Measure performance in consumers

Use consumer tasks and commands to establish performance benefit. Separate task
execution, prerequisites, configuration, fingerprinting, cache restoration, and
command wall time. Compare equivalent revisions, artifacts, environments, and
measurement boundaries; separate cold and warm runs. Report repeated measurements
and their spread. Do not present warmup, overlapping task totals, or historical
cache-origin metadata as measured implementation savings.

### V. Verify behavior with focused tests

Use the existing test conventions and supported Gradle matrix. Add meaningful
regression coverage for changed behavior and use real Gradle TestKit consumers
where task execution, classloader isolation, or configuration cache matters.
Check findings and task outcomes as well as build success. Failures must remain
visible; a failed analysis must not become successful empty results. Routine
documentation changes need appropriate document/tooling checks rather than new
runtime tests.

## Project constraints

- Reuse this multi-project Gradle build, its wrapper, dependency declarations,
  locking, and existing Java/Kotlin conventions.
- The Gradle plugin uses a Java 17 toolchain and the core a Java 8 toolchain. The
  supported test matrix is defined in `SupportedGradleVersions.kt`; consult it
  rather than treating versions in an old spec as permanent support policy.
- Keep contributor tooling, including Spec Kit, outside plugin runtime dependencies
  and consumer task graphs.
- Keep OSS artifacts self-contained: use repository paths, public references,
  public reproducible measurements, and reproducible fixtures. Do not include internal issue
  identifiers, internal service names, private links, or machine-specific paths.

## Feature workflow

Use `specs/<number>-<feature>/spec.md` for outcomes, requirements, and measurable
acceptance; `plan.md` for design and validation; and `tasks.md` for implementation
work. Add research or execution instructions when they help the change. Keep
requirements, plan, and tasks consistent as evidence changes. Mark verification
complete only when it was actually performed and record material limitations.

## Governance

This file records project requirements grounded in the existing build and the
agreed consumer compatibility and performance goals. It does not supersede user
instructions or repository policy. Amend it alongside affected plans in a normal
reviewable change; do not invent extra approval steps. Assess versioning and
migration impact when a requirement or public contract changes.

**Version**: 1.0.0 | **Adopted**: 2026-10-29 | **Last Amended**: 2026-10-29
