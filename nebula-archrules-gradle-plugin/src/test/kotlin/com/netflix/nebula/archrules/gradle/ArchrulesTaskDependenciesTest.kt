package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.Gradle
import nebula.test.dsl.TestProjectRunner
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.repositories
import nebula.test.dsl.run
import nebula.test.dsl.settings
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.util.jar.JarOutputStream

internal class ArchrulesTaskDependenciesTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @ValueSource(strings = ["9.7.1", "9.9.0"])
    fun `a standalone main check builds its self-referenced artifact`(version: String) {
        val fixture = fixture()

        val first = fixture.runOn(version, ":consumer:checkArchRulesMain")

        assertThat(first.task(":consumer:jar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertChecked(first, "main")
        val expectedResults = results("main")

        // Delete the analysis output so cache reuse must execute the check, not just skip it.
        assertThat(report("main").delete()).isTrue()
        val reused = fixture.runOn(version, ":consumer:checkArchRulesMain")

        assertThat(reused.output).contains("Reusing configuration cache")
        assertThat(reused.task(":consumer:jar")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
        assertChecked(reused, "main")
        assertThat(results("main")).isEqualTo(expectedResults)
    }

    @ParameterizedTest
    @ValueSource(strings = ["9.7.1", "9.9.0"])
    fun `a custom source set check can run alongside its self-referenced jar`(version: String) {
        val fixture = fixture()

        val result = fixture.runOn(version, ":consumer:jar", ":consumer:checkArchRulesIntegrationTest")

        assertThat(result.task(":consumer:jar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertChecked(result, "integrationTest")
    }

    @ParameterizedTest
    @ValueSource(strings = ["9.7.1", "9.9.0"])
    fun `a self-reference uses the selected replacement artifact producer`(version: String) {
        val fixture = fixture(replacementArtifact = true)

        val result = fixture.runOn(version, ":consumer:replacementJar", ":consumer:checkArchRulesMain")

        assertThat(result.task(":consumer:replacementJar")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(result.task(":consumer:jar")).isNull()
        assertChecked(result, "main")
    }

    @ParameterizedTest
    @ValueSource(strings = ["9.7.1", "9.9.0"])
    fun `a check without a self-reference does not build an unrelated jar`(version: String) {
        val fixture = fixture(selfReference = false)

        val first = fixture.runOn(version, ":consumer:checkArchRulesMain")

        assertThat(first.task(":consumer:jar")).isNull()
        assertChecked(first, "main")
        val expectedResults = results("main")

        assertThat(report("main").delete()).isTrue()
        val reused = fixture.runOn(version, ":consumer:checkArchRulesMain")

        assertThat(reused.output).contains("Reusing configuration cache")
        assertThat(reused.task(":consumer:jar")).isNull()
        assertChecked(reused, "main")
        assertThat(results("main")).isEqualTo(expectedResults)
    }

    private fun fixture(selfReference: Boolean = true, replacementArtifact: Boolean = false): TestProjectRunner {
        val repository = projectDir.resolve("repository")
        val module = repository.resolve("com/example/fixture/bridge/1.0").apply { mkdirs() }
        JarOutputStream(module.resolve("bridge-1.0.jar").outputStream()).use { }
        val dependencies = if (selfReference) """
            <dependencies>
                <dependency>
                    <groupId>com.example.fixture</groupId>
                    <artifactId>consumer</artifactId>
                    <version>1.0</version>
                </dependency>
            </dependencies>
        """ else ""
        module.resolve("bridge-1.0.pom").writeText("""
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example.fixture</groupId>
                <artifactId>bridge</artifactId>
                <version>1.0</version>
                $dependencies
            </project>
        """.trimIndent())

        return testProject(projectDir) {
            settings { name("self-referencing-classpath") }
            subProject("rules") {
                libraryWithRulesProject()
                src {
                    sourceSet("archRules") { exampleDeprecatedArchRule() }
                }
            }
            subProject("consumer") {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    maven(repository.toURI().toString())
                    mavenCentral()
                }
                // Model an external library depending back on the consumer's published coordinate.
                // Prefer the local project when that coordinate is already in the build.
                rawBuildScript("""
                    group = "com.example.fixture"
                    version = "1.0"
                    dependencies {
                        implementation("com.example.fixture:bridge:1.0")
                        add("archRules", project(":rules"))
                    }
                    configurations.configureEach {
                        resolutionStrategy.preferProjectModules()
                    }
                    sourceSets.create("integrationTest") {
                        compileClasspath += sourceSets.main.get().output
                    }
                    configurations.named("integrationTestImplementation") {
                        extendsFrom(configurations.implementation.get())
                    }
                """.trimIndent())
                if (replacementArtifact) {
                    rawBuildScript("""
                        val replacementJar = tasks.register<Jar>("replacementJar") {
                            from(sourceSets.main.get().output)
                            archiveClassifier.set("replacement")
                        }
                        listOf("apiElements", "runtimeElements").forEach { name ->
                            configurations.named(name) {
                                outgoing.artifacts.clear()
                                outgoing.artifact(replacementJar)
                            }
                        }
                    """.trimIndent())
                }
                src {
                    main {
                        exampleLibraryClass()
                        exampleDeprecatedUsage()
                    }
                    sourceSet("integrationTest") {
                        exampleDeprecatedUsage("IntegrationCode")
                    }
                }
            }
        }
    }

    private fun TestProjectRunner.runOn(version: String, vararg tasks: String): BuildResult = run(
        *tasks,
        "--configuration-cache", "--configuration-cache-problems=fail",
        "--no-build-cache", "--parallel", "--max-workers=2", "--stacktrace"
    ) {
        withGradle(Gradle.ofVersion(version))
    }

    private fun assertChecked(result: BuildResult, sourceSet: String) {
        val taskName = "checkArchRules" + sourceSet.replaceFirstChar { it.uppercaseChar() }
        assertThat(result.task(":consumer:$taskName")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
        assertThat(report(sourceSet)).exists()
        assertThat(results(sourceSet).map { it.status }).contains(RuleResultStatus.FAIL)
    }

    private fun report(sourceSet: String): File =
        projectDir.resolve("consumer/build/reports/archrules/$sourceSet.data")

    private fun results(sourceSet: String): List<RuleResult> = ViolationsUtil.readDetails(report(sourceSet))
}
