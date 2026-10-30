package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.withGradle
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

internal class ReportPerformanceIntegrationTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `analysis and text reports execute after configuration cache reuse`(version: SupportedGradleVersion) {
        fixture()
        val commands = listOf("archRulesMarkdownReport", "archRulesConsoleReport", "enforceArchRules")
        val markdown = projectDir.resolve("build/reports/archrules/report.md")

        val first = runner(version, commands).build()
        assertThat(first.output).contains("Configuration cache entry stored", "ArchRule Summary:")
        assertThat(first.task(":checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val expectedResults = results()
        assertThat(expectedResults.map { it.status }).containsExactly(
            RuleResultStatus.PASS, RuleResultStatus.FAIL, RuleResultStatus.NO_MATCH
        )
        assertThat(expectedResults.first().rule.priority).isEqualTo(Priority.HIGH)
        val expectedMarkdown = markdown.readText()
        assertThat(expectedMarkdown).contains("| passing | HIGH | 0 |", "| failing | MEDIUM | 1 |", "ConsumerClass.java:")

        assertThat(dataFile().delete()).isTrue()
        assertThat(markdown.delete()).isTrue()
        val reused = runner(version, commands).build()

        assertThat(reused.output).contains("Reusing configuration cache", "ArchRule Summary:")
        (commands + "checkArchRulesMain").forEach { task ->
            assertThat(reused.task(":$task")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        }
        assertThat(reused.task(":compileJava")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(results()).isEqualTo(expectedResults)
        assertThat(markdown.readText()).isEqualTo(expectedMarkdown)

        val unchanged = runner(version, commands).build()
        assertThat(unchanged.output).contains("Reusing configuration cache")
        assertThat(unchanged.task(":checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertThat(markdown.readText()).isEqualTo(expectedMarkdown)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `result files restored from the task cache retain their format`(version: SupportedGradleVersion) {
        fixture()
        val commands = listOf("checkArchRulesMain")
        val first = runner(version, commands, buildCache = true).build()
        assertThat(first.task(":checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.SUCCESS)
        val expectedBytes = dataFile().readBytes()
        val expectedResults = results()
        assertThat(expectedResults).hasSize(3)
        assertThat(dataFile().delete()).isTrue()

        val restored = runner(version, commands, buildCache = true).build()

        assertThat(restored.task(":checkArchRulesMain")!!.outcome).isEqualTo(TaskOutcome.FROM_CACHE)
        assertThat(dataFile().readBytes()).isEqualTo(expectedBytes)
        assertThat(results()).isEqualTo(expectedResults)
    }

    private fun fixture() {
        val metadata = Properties().apply {
            ReportPerformanceIntegrationTest::class.java.classLoader
                .getResourceAsStream("plugin-under-test-metadata.properties")!!.use { load(it) }
        }
        val classpath = metadata.getProperty("implementation-classpath").split(File.pathSeparator).map(::File)
        val ruleJar = compileRules(classpath)
        val paths = (classpath + ruleJar).joinToString(", ") { "'${it.invariantSeparatorsPath.replace("'", "\\'")}'" }
        projectDir.resolve("settings.gradle").writeText("""
rootProject.name = 'report-performance-consumer'
buildCache { local { directory = file('local-build-cache') } }
""")
        projectDir.resolve("gradle.properties").writeText("org.gradle.workers.max=2\n")
        projectDir.resolve("build.gradle").writeText("""
plugins {
    id 'java'
    id 'com.netflix.nebula.archrules.runner'
}
tasks.withType(com.netflix.nebula.archrules.gradle.CheckRulesTask).configureEach {
    rulesClasspath.setFrom(files($paths))
    priorityOverridesByName.put('passing', com.tngtech.archunit.lang.Priority.HIGH)
}
['archRulesMarkdownReport', 'archRulesConsoleReport', 'enforceArchRules'].each { report ->
    tasks.named(report) { dataFiles.setFrom(tasks.named('checkArchRulesMain')) }
}
""")
        projectDir.resolve("src/main/java/example/ConsumerClass.java").apply {
            parentFile.mkdirs()
            writeText("package example; public class ConsumerClass {}")
        }
    }

    private fun compileRules(classpath: List<File>): File {
        val source = projectDir.resolve("fixture-src/example/FixtureRules.java").apply { parentFile.mkdirs() }
        source.writeText("""
package example;
import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Map;
import java.util.LinkedHashMap;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
public class FixtureRules implements ArchRulesService {
    public Map<String, ArchRule> getRules() {
        Map<String, ArchRule> rules = new LinkedHashMap<>();
        rules.put("passing", classes().should().bePublic());
        rules.put("failing", classes().should().beAnnotatedWith(Deprecated.class));
        rules.put("empty", classes().that().haveSimpleName("NeverMatched").should().bePublic().allowEmptyShould(false));
        return rules;
    }
}
""")
        val classes = projectDir.resolve("fixture-classes").apply { mkdirs() }
        val output = ByteArrayOutputStream()
        val compiled = ToolProvider.getSystemJavaCompiler().run(
            null, output, output, "--release", "17", "-classpath",
            classpath.joinToString(File.pathSeparator), "-d", classes.absolutePath, source.absolutePath
        )
        assertThat(compiled).withFailMessage(output.toString()).isZero()
        return projectDir.resolve("fixture-rules.jar").also { jar ->
            JarOutputStream(jar.outputStream()).use { archive ->
                classes.walkTopDown().filter { it.isFile }.forEach { file ->
                    archive.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath).apply { time = 0 })
                    file.inputStream().use { it.copyTo(archive) }
                    archive.closeEntry()
                }
                archive.putNextEntry(JarEntry("META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService").apply { time = 0 })
                archive.write("example.FixtureRules".toByteArray())
                archive.closeEntry()
            }
        }
    }

    private fun runner(version: SupportedGradleVersion, commands: List<String>, buildCache: Boolean = false): GradleRunner =
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath().withArguments(
            commands + listOf("--configuration-cache", "--configuration-cache-problems=fail",
                if (buildCache) "--build-cache" else "--no-build-cache", "--stacktrace")
        ).apply { withGradle(version.version) }

    private fun dataFile(): File = projectDir.resolve("build/reports/archrules/main.data")

    private fun results(): List<RuleResult> = ObjectInputStream(dataFile().inputStream()).use { input ->
        List(input.readInt()) { input.readObject() as RuleResult }
    }
}
