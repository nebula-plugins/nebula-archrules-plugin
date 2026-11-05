package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.withGradle
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.stream.Stream
import javax.tools.ToolProvider

/** Strict configuration-cache and Isolated Projects regressions for supported workflows. */
class IsolatedProjectsAssessmentTest {
    @TempDir
    lateinit var projectDir: File

    enum class Mode { CONFIGURATION_CACHE, ISOLATED_PROJECTS }
    enum class Shape(val consumer: String, val projectPath: String = consumer.replace('/', ':')) {
        SINGLE(""), MULTI("app"), NESTED("group/app"), CUSTOM("components/remapped-app", "group:app")
    }

    companion object {
        @JvmStatic
        fun scenarios(): Stream<Arguments> = SupportedGradleVersion.entries.flatMap { version ->
            Mode.entries.flatMap { mode -> Shape.entries.map { Arguments.of(version, mode, it) } }
        }.stream()

        @JvmStatic
        fun versionsAndModes(): Stream<Arguments> = SupportedGradleVersion.entries.flatMap { version ->
            Mode.entries.map { Arguments.of(version, it) }
        }.stream()

        @JvmStatic
        fun childReports(): Stream<Arguments> = SupportedGradleVersion.entries.flatMap { version ->
            listOf(Shape.MULTI, Shape.NESTED).flatMap { shape ->
                listOf("check", "archRulesGithubReport").map { Arguments.of(version, shape, it) }
            }
        }.stream()

        @JvmStatic
        fun enabledChildReports(): Stream<Arguments> = SupportedGradleVersion.entries.flatMap { version ->
            Mode.entries.flatMap { mode ->
                listOf(Shape.MULTI, Shape.NESTED, Shape.CUSTOM).flatMap { shape ->
                    listOf("check", "archRulesGithubReport").map { Arguments.of(version, mode, shape, it) }
                }
            }
        }.stream()

        private val rulesSource = """
            package example;
            import com.netflix.nebula.archrules.core.ArchRulesService;
            import com.tngtech.archunit.lang.ArchRule;
            import com.tngtech.archunit.lang.Priority;
            import java.util.Map;
            import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
            public class FixtureRules implements ArchRulesService {
                public Map<String, ArchRule> getRules() {
                    return Map.of("deprecated", classes().should().beAnnotatedWith(Deprecated.class));
                }
            }
        """.trimIndent()
    }

    @ParameterizedTest(name = "reports: {0}, {1}, {2}")
    @MethodSource("scenarios")
    fun `reports preserve behavior across cache reuse and input changes`(
        version: SupportedGradleVersion, mode: Mode, shape: Shape
    ) {
        fixture(shape)
        val prefix = taskPrefix(shape)
        val commands = if (shape == Shape.SINGLE) {
            listOf("check", "-Parchrules.github.enabled=true")
        } else listOf("archRulesAggregateMarkdownReport", "archRulesAggregateConsoleReport", "${prefix}enforceArchRules") +
            if (shape.projectPath.startsWith("group:")) listOf(":group:archRulesAggregateMarkdownReport") else emptyList()
        val markdown = projectDir.resolve("build/reports/archrules/report.md")
        val first = run(version, mode, commands)
        assertStored(first)
        assertThat(first.task("${prefix}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertViolation(shape, Priority.LOW)
        assertThat(markdown.readText()).contains("| deprecated | LOW | 1 |", "Consumer.java:")
        val expected = markdown.readText()
        if (shape.projectPath.startsWith("group:")) {
            assertThat(projectDir.resolve("group/build/reports/archrules/report.md").readText()).isEqualTo(expected)
        }
        if (shape != Shape.SINGLE) {
            assertThat(first.task(":rules:archRulesJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
            assertThat(first.tasks.map { it.path }).noneMatch { it.startsWith(":empty:") }
        } else {
            assertThat(projectDir.resolve("build/reports/archrules/report.json").readText()).contains("deprecated")
            assertThat(projectDir.resolve("build/reports/archrules/github-annotations.json").readText()).contains("Consumer.java")
        }

        // Force execution from the restored task graph, independently of task output caching.
        assertThat(dataFile(shape).delete()).isTrue()
        assertThat(markdown.delete()).isTrue()
        val restored = run(version, mode, commands)
        assertReused(restored)
        assertThat(restored.task("${prefix}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(markdown.readText()).isEqualTo(expected)
        assertViolation(shape, Priority.LOW)
        val unchanged = run(version, mode, commands)
        assertReused(unchanged)
        assertThat(unchanged.task("${prefix}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

        // Extension changes invalidate configuration and change the worker's observable priority.
        consumerDir(shape).resolve("build.gradle").appendText("\narchRules { ruleName('deprecated') { priority('HIGH') } }\n")
        val reconfigured = run(version, mode, commands)
        assertStored(reconfigured)
        assertThat(reconfigured.output).doesNotContain("Reusing configuration cache")
        assertThat(reconfigured.task("${prefix}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertViolation(shape, Priority.HIGH)
        assertThat(markdown.readText()).contains("| deprecated | HIGH | 1 |")

        // With GitHub reporting, source collection also becomes a configuration input.
        // Characterize that conservative invalidation rather than claiming reuse on every edit.
        consumerDir(shape).resolve("src/main/java/example/Consumer.java")
            .writeText("package example; @Deprecated public class Consumer {}")
        val changedSource = run(version, mode, commands)
        if (shape == Shape.SINGLE) {
            assertStored(changedSource)
            assertThat(changedSource.output).doesNotContain("Reusing configuration cache")
        } else assertReused(changedSource)
        assertThat(changedSource.task("${prefix}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(results(shape).single().status).isEqualTo(RuleResultStatus.PASS)
        assertThat(markdown.readText()).contains("| deprecated | HIGH | 0 |")
        assertReused(run(version, mode, commands))
    }

    @ParameterizedTest(name = "library: {0}, {1}")
    @MethodSource("versionsAndModes")
    fun `library packaging documentation and publishing reuse configuration`(version: SupportedGradleVersion, mode: Mode) {
        settings()
        library(projectDir)
        val commands = listOf("archRulesJar", "generateRulesDocumentation", "publishMavenPublicationToAssessmentRepository")
        val first = run(version, mode, commands)
        assertStored(first)
        val jar = projectDir.resolve("build/libs/assessment-1.0-arch-rules.jar")
        val docs = projectDir.resolve("build/docs/archrules.md")
        assertLibrary(jar, docs)
        val expectedDocs = docs.readText()
        assertThat(projectDir.resolve("build/repository/example/assessment/1.0/assessment-1.0.module").readText())
            .contains("archRulesRuntimeElements", "assessment-1.0-arch-rules.jar", "assessment-1.0.jar", "arch-rules")
        assertThat(jar.delete()).isTrue()
        assertThat(docs.delete()).isTrue()
        val restored = run(version, mode, commands)
        assertReused(restored)
        assertLibrary(jar, docs)
        assertThat(docs.readText()).isEqualTo(expectedDocs)
        assertThat(restored.task(":archRulesJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(restored.task(":generateRulesDocumentation")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)

        projectDir.resolve("src/archRules/java/example/FixtureRules.java")
            .writeText(rulesSource.replace("classes().should()", "classes().that().arePublic().should()"))
        val changed = run(version, mode, commands)
        assertReused(changed)
        assertThat(changed.task(":generateServicesRegistry")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(changed.task(":archRulesJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(docs.readText()).contains("are public")
        assertThat(docs.readText()).isNotEqualTo(expectedDocs)
    }

    @ParameterizedTest(name = "lazy GitHub report: {0}, {1}")
    @MethodSource("versionsAndModes")
    fun `help and library packaging do not realize child GitHub reports`(version: SupportedGradleVersion, mode: Mode) {
        fixture(Shape.MULTI)
        consumerDir(Shape.MULTI).resolve("build.gradle").appendText("\n" + """
            tasks.withType(com.netflix.nebula.archrules.gradle.GithubReportTask).configureEach {
                throw new GradleException('GitHub report should remain unrealized')
            }
        """.trimIndent())
        val commands = listOf("help", ":rules:archRulesJar")
        val first = run(version, mode, commands)
        assertStored(first)
        assertThat(first.task(":help")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(first.task(":rules:archRulesJar")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertReused(run(version, mode, commands))
    }

    @ParameterizedTest(name = "disabled child GitHub report: {0}, {1}, {2}")
    @MethodSource("childReports")
    fun `child check and qualified report store and reuse configuration when GitHub is disabled`(
        version: SupportedGradleVersion, shape: Shape, command: String
    ) {
        fixture(shape)
        val commands = listOf(taskPrefix(shape) + command)
        // Keep the existing qualified-request semantics: without explicit enablement this task skips.
        Mode.entries.forEach { mode ->
            val first = run(version, mode, commands)
            assertStored(first)
            assertThat(first.task("${taskPrefix(shape)}archRulesGithubReport")!!.outcome)
                .isEqualTo(TaskOutcome.SKIPPED)
            val restored = run(version, mode, commands)
            assertReused(restored)
            assertThat(restored.task("${taskPrefix(shape)}archRulesGithubReport")!!.outcome)
                .isEqualTo(TaskOutcome.SKIPPED)
        }
    }

    @ParameterizedTest(name = "enabled child GitHub report: {0}, {1}, {2}, {3}")
    @MethodSource("enabledChildReports")
    fun `enabled child reports preserve root relative annotations when configuration is restored`(
        version: SupportedGradleVersion, mode: Mode, shape: Shape, command: String
    ) {
        fixture(shape)
        val commands = if (command == "check") {
            listOf(taskPrefix(shape) + command, "-Parchrules.github.enabled=true")
        } else {
            consumerDir(shape).resolve("build.gradle").appendText("\narchRules { githubReportEnabled = true }\n")
            listOf(taskPrefix(shape) + command)
        }
        val report = consumerDir(shape).resolve("build/reports/archrules/github-annotations.json")
        val first = run(version, mode, commands)
        assertStored(first)
        assertGithubReport(first, shape, report)
        assertViolation(shape, Priority.LOW)
        val expected = report.readText()

        // Delete outputs to prove that workers and reporting run from the restored graph.
        assertThat(dataFile(shape).delete()).isTrue()
        assertThat(report.delete()).isTrue()
        val restored = run(version, mode, commands)
        assertReused(restored)
        assertThat(restored.task("${taskPrefix(shape)}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertGithubReport(restored, shape, report)
        assertThat(report.readText()).isEqualTo(expected)
        assertViolation(shape, Priority.LOW)

        val unchanged = run(version, mode, commands)
        assertReused(unchanged)
        assertThat(unchanged.task("${taskPrefix(shape)}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertGithubReport(unchanged, shape, report)
        assertThat(report.readText()).isEqualTo(expected)
    }

    @ParameterizedTest(name = "source discovery in remapped child: {0}, {1}")
    @MethodSource("versionsAndModes")
    fun `unqualified GitHub request discovers added and removed sources in a custom project directory`(
        version: SupportedGradleVersion, mode: Mode
    ) {
        val shape = Shape.CUSTOM
        fixture(shape)
        val commands = listOf("archRulesGithubReport")
        val report = consumerDir(shape).resolve("build/reports/archrules/github-annotations.json")
        val first = run(version, mode, commands)
        assertStored(first)
        assertGithubReport(first, shape, report)
        val expected = report.readText()

        val addedSource = write(consumerDir(shape), "src/main/java/example/Additional.java",
            "package example; public class Additional {}")
        val added = run(version, mode, commands)
        assertStored(added)
        assertThat(added.task("${taskPrefix(shape)}checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(added.task("${taskPrefix(shape)}archRulesGithubReport")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val annotations = JSONObject(report.readText()).getJSONArray("annotations")
        assertThat((0 until annotations.length()).map { annotations.getJSONObject(it).getString("path") })
            .containsExactlyInAnyOrder("${shape.consumer}/src/main/java/example/Consumer.java",
                "${shape.consumer}/src/main/java/example/Additional.java")

        assertThat(addedSource.delete()).isTrue()
        val removed = run(version, mode, commands)
        assertStored(removed)
        assertGithubReport(removed, shape, report)
        assertThat(report.readText()).isEqualTo(expected)
        assertReused(run(version, mode, commands))
    }

    private fun assertGithubReport(result: BuildResult, shape: Shape, report: File) {
        val sourcePath = "${shape.consumer}/src/main/java/example/Consumer.java"
        assertThat(result.task("${taskPrefix(shape)}archRulesGithubReport")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.output).contains("::notice file=$sourcePath,line=1,")
        val annotations = JSONObject(report.readText()).getJSONArray("annotations")
        assertThat(annotations.length()).isEqualTo(1)
        val annotation = annotations.getJSONObject(0)
        assertThat(annotation.getString("path")).isEqualTo(sourcePath)
        assertThat(annotation.getString("annotation_level")).isEqualTo("notice")
        // Preserve the existing JSON behavior for a class-level location (console defaults to 1).
        assertThat(annotation.getInt("start_line")).isZero()
    }

    private fun fixture(shape: Shape) {
        settings(if (shape == Shape.SINGLE) "" else "include 'rules', '${shape.projectPath}', 'empty'")
        if (shape == Shape.CUSTOM) {
            projectDir.resolve("settings.gradle").appendText(
                "project(':group:app').projectDir = file('${shape.consumer}')\n"
            )
        }
        if (shape != Shape.SINGLE) {
            write(projectDir, "build.gradle", "plugins { id 'com.netflix.nebula.archrules.aggregate' }\n")
            library(projectDir.resolve("rules"))
            write(projectDir.resolve("empty"), "build.gradle", "plugins { id 'base' }\n")
            if (shape.projectPath.startsWith("group:")) {
                write(projectDir.resolve("group"), "build.gradle", "plugins { id 'com.netflix.nebula.archrules.aggregate' }\n")
            }
        }
        val rules = if (shape == Shape.SINGLE) "files(${quotedPaths(listOf(compileRules()))})" else "project(':rules')"
        write(consumerDir(shape), "build.gradle", """
            plugins { id 'java'; id 'com.netflix.nebula.archrules.runner' }
            dependencies { archRules($rules) }
            archRules {
                ruleName('deprecated') { priority('LOW') }
                consoleDetailsThreshold('LOW')
            }
            tasks.withType(com.netflix.nebula.archrules.gradle.CheckRulesTask).configureEach {
                rulesClasspath.from(files(${quotedPaths(classpath())}))
            }
            tasks.withType(com.netflix.nebula.archrules.gradle.PrintJsonReportTask).configureEach {
                reportingClasspath.setFrom(files(${quotedPaths(reportingClasspath())}))
            }
            tasks.withType(com.netflix.nebula.archrules.gradle.GithubReportTask).configureEach {
                reportingClasspath.setFrom(files(${quotedPaths(reportingClasspath())}))
            }
        """.trimIndent())
        write(consumerDir(shape), "src/main/java/example/Consumer.java", "package example; public class Consumer {}")
    }

    private fun library(dir: File) {
        write(dir, "build.gradle", """
            plugins { id 'java-library'; id 'com.netflix.nebula.archrules.library'; id 'maven-publish' }
            group = 'example'
            version = '1.0'
            // Use the actual plugin runtime locally so assessment does not depend on published versions.
            configurations.archRulesImplementation.dependencies.clear()
            dependencies { archRulesImplementation(files(${quotedPaths(classpath())})) }
            publishing {
                publications { maven(MavenPublication) { from components.java } }
                repositories { maven { name = 'assessment'; url = layout.buildDirectory.dir('repository') } }
            }
        """.trimIndent())
        write(dir, "src/archRules/java/example/FixtureRules.java", rulesSource)
    }

    private fun settings(includes: String = "") {
        write(projectDir, "settings.gradle", "rootProject.name = 'assessment'\n$includes\n")
        write(projectDir, "gradle.properties", "org.gradle.workers.max=2\n")
    }

    private fun classpath(): List<File> = Properties().apply {
        IsolatedProjectsAssessmentTest::class.java.classLoader
            .getResourceAsStream("plugin-under-test-metadata.properties")!!.use { load(it) }
    }.getProperty("implementation-classpath").split(File.pathSeparator).map(::File)

    private fun reportingClasspath(): List<File> = classpath() + listOf(
        "tools.jackson.databind.json.JsonMapper", "tools.jackson.core.JsonToken",
        "com.fasterxml.jackson.annotation.JsonProperty"
    ).map { File(Class.forName(it).protectionDomain.codeSource.location.toURI()) }

    private fun compileRules(): File {
        val source = write(projectDir, "fixture-src/example/FixtureRules.java", rulesSource)
        val classes = projectDir.resolve("fixture-classes").apply { mkdirs() }
        val output = ByteArrayOutputStream()
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, output, output, "--release", "17", "-classpath",
            classpath().joinToString(File.pathSeparator), "-d", classes.absolutePath, source.absolutePath))
            .withFailMessage(output.toString()).isZero()
        return projectDir.resolve("fixture-rules.jar").also { jar ->
            JarOutputStream(jar.outputStream()).use { out ->
                classes.walkTopDown().filter { it.isFile }.forEach { file ->
                    out.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
                out.putNextEntry(JarEntry("META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"))
                out.write("example.FixtureRules".toByteArray())
                out.closeEntry()
            }
        }
    }

    private fun runner(version: SupportedGradleVersion, mode: Mode, commands: List<String>): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(
            // Test code edits fixture files between builds, outside Gradle's filesystem watcher.
            commands + listOf("--configuration-cache", "--configuration-cache-problems=fail",
                "--no-build-cache", "--no-watch-fs", "--stacktrace") +
                if (mode == Mode.ISOLATED_PROJECTS) {
                    // Stable CLI spelling was introduced in 9.7; 9.2 needs the experimental property.
                    listOf(if (version == SupportedGradleVersion.GRADLE_9_2)
                        "-Dorg.gradle.unsafe.isolated-projects=true" else "--isolated-projects")
                } else emptyList()
        ).apply { withGradle(version.version) }

    private fun run(version: SupportedGradleVersion, mode: Mode, commands: List<String>): BuildResult =
        runner(version, mode, commands).build()

    private fun assertStored(result: BuildResult) {
        assertThat(result.output).contains("Configuration cache entry stored")
    }

    private fun assertReused(result: BuildResult) {
        assertThat(result.output).contains("Reusing configuration cache")
    }

    private fun consumerDir(shape: Shape): File = projectDir.resolve(shape.consumer)
    private fun taskPrefix(shape: Shape): String = ":" + if (shape.projectPath.isEmpty()) "" else shape.projectPath + ":"
    private fun dataFile(shape: Shape): File = consumerDir(shape).resolve("build/reports/archrules/main.data")
    private fun results(shape: Shape): List<RuleResult> = ObjectInputStream(dataFile(shape).inputStream()).use { input ->
        List(input.readInt()) { input.readObject() as RuleResult }
    }

    private fun assertViolation(shape: Shape, priority: Priority) {
        val result = results(shape).single()
        assertThat(result.status).isEqualTo(RuleResultStatus.FAIL)
        assertThat(result.rule.ruleName).isEqualTo("deprecated")
        assertThat(result.rule.priority).isEqualTo(priority)
        assertThat(result.message).contains("Consumer")
    }

    private fun assertLibrary(jar: File, docs: File) {
        JarFile(jar).use { archive ->
            assertThat(archive.getEntry("example/FixtureRules.class")).isNotNull()
            val service = archive.getEntry("META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService")
            assertThat(archive.getInputStream(service).bufferedReader().readText()).isEqualTo("example.FixtureRules")
        }
        assertThat(docs.readText()).contains("### deprecated", "example.FixtureRules")
    }

    private fun quotedPaths(files: List<File>): String = files.joinToString(", ") {
        "'${it.invariantSeparatorsPath.replace("'", "\\'")}'"
    }

    private fun write(dir: File, path: String, content: String): File = dir.resolve(path).apply {
        parentFile.mkdirs()
        writeText(content)
    }
}
