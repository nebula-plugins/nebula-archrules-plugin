package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import org.gradle.kotlin.dsl.findByType
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class ArchrulesAggregateReportPluginTest {
    @TempDir
    lateinit var projectDir: File

    private fun TestProjectBuilder.setup(withFailures: Boolean = true) {
        properties {
            buildCache(true)
            configurationCache(true)
            property("org.gradle.unsafe.isolated-projects","true")
        }
        rootProject {
            plugins {
                id("com.netflix.nebula.archrules.aggregate")
            }
        }
        subProject("library") {
            plugins {
                id("java")
            }
            src {
                main {
                    exampleLibraryClass()
                }
            }
        }
        subProject("sub1") {
            plugins {
                id("java")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                mavenCentral()
            }
            dependencies(
                """implementation(project(":library"))""",
                """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
            )
            src {
                main {
                    if (withFailures) {
                        exampleDeprecatedUsage("FailingCode1")
                    }
                }
            }
        }
        subProject("sub2") {
            plugins {
                id("java")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                mavenCentral()
            }
            dependencies(
                """implementation(project(":library"))""",
                """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
            )
            src {
                main {
                    if (withFailures) {
                        exampleDeprecatedUsage("FailingCode2")
                    }
                }
            }
        }
    }

    @Test
    fun `settings defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.netflix.nebula.archrules.aggregate")
        val extension = project.extensions.findByType<ArchrulesAggregateExtension>()!!
        assertThat(extension.skipPassingSummaries.get()).isFalse()
        assertThat(extension.consoleDetailsThreshold.get()).isEqualTo(Priority.MEDIUM)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test console`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setup()
        }
        val result = runner.run("archRulesAggregateConsoleReport") {
            forwardOutput()
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.task(":sub1:checkArchRulesMain"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.output)
            .contains("deprecatedForRemoval  MEDIUM     (2 failures)")
            .contains("deprecated            LOW        (4 failures)")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test markdown`(gradle: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setup()
        }
        val result = runner.run("archRulesAggregateMarkdownReport") {
            forwardOutput()
            withGradle(gradle.version)
        }
        assertThat(result)
            .hasNoDeprecationWarnings()
            .hasNoMutableStateWarnings()
        assertThat(result.task(":sub1:checkArchRulesMain"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":archRulesAggregateMarkdownReport"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(projectDir.resolve("build/reports/archrules/report.md")).exists()
    }

    @Test
    fun `test passing skip`() {
        val runner = testProject(projectDir) {
            setup(withFailures = false)
            rootProject {
                rawBuildScript(
                    """
archRulesAggregate {
    skipPassingSummaries = true
}
"""
                )
            }
        }
        val result = runner.run("archRulesAggregateConsoleReport") {
            forwardOutput()
        }
        assertThat(result.output)
            .doesNotContain("deprecatedForRemoval")
            .doesNotContain("deprecated")
    }

    @Test
    fun `test details threshold`() {
        val runner = testProject(projectDir) {
            setup()
            rootProject {
                rawBuildScript(
                    """
archRulesAggregate {
    consoleDetailsThreshold("LOW")
}
"""
                )
            }
        }
        val result = runner.run("archRulesAggregateConsoleReport") {
            forwardOutput()
        }
        assertThat(result.output)
            .contains("Method <com.example.consumer.FailingCode1.aMethod()> calls method <com.example.library.LibraryClass.deprecatedApi()>")
    }

    @Test
    fun `test empty subproject`() {
        val runner = testProject(projectDir) {
            setup()
            subProject("empty") {
                plugins {
                    id("base")
                }
                rawBuildScript(
                    // language=kotlin
                    """
val jarTask = tasks.register<Jar>("someJar")
artifacts {
    add("default", jarTask)
}
"""
                )
            }
        }
        val result = runner.run("archRulesAggregateConsoleReport")
        assertThat(result.task(":archRulesAggregateConsoleReport"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.output).doesNotContain("Archrules data read failed")
    }

    @Test
    fun test_nested_subproject() {
        val runner = testProject(projectDir) {
            setup()
            subProject(":group:nested") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """implementation(project(":library"))""",
                    """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
                )
            }
        }
        val result = runner.run("archRulesAggregateConsoleReport")
        assertThat(result.task(":archRulesAggregateConsoleReport"))
            .hasOutcome(TaskOutcome.SUCCESS)
    }
}
