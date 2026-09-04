package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.ProjectBuilder
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.run
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.version
import nebula.test.dsl.withGradle
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

internal class IntegrationTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun test(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            projectWithRules()
            projectWithCodeUsingDeprecatedCode()
        }

        val result = runner.run("check", "--stacktrace") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":library-with-rules:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)

        assertThat(result.task(":code-to-check:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":code-to-check:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":code-to-check:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)

        assertThat(result.task(":code-to-check:enforceArchRules"))
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val reportsDir = projectDir.resolve("code-to-check/build/reports/archrules")

        assertThat(reportsDir.exists())
        assertThat(reportsDir.resolve("main.data")).exists().isNotEmpty
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test variant resolution`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            projectWithRules {
                plugins {
                    kotlin("jvm") version ("2.2.20")
                }
            }
            projectWithCodeUsingDeprecatedCode()
        }

        val result = runner.run("check", "--stacktrace") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":library-with-rules:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)

        assertThat(result.task(":code-to-check:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":code-to-check:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":code-to-check:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val reportsDir = projectDir.resolve("code-to-check/build/reports/archrules")

        assertThat(reportsDir.exists())
        assertThat(reportsDir.resolve("main.data")).exists().isNotEmpty

        val diArchRulesResult = runner.run(
            ":code-to-check:dependencyInsight",
            "--configuration", "mainArchrulesRuntime",
            "--dependency", "library-with-rules"
        )
        assertThat(diArchRulesResult.output).contains("Variant archRulesRuntimeElements:")

        val diRuntimeResult = runner.run(
            ":code-to-check:dependencyInsight",
            "--configuration", "runtimeClasspath",
            "--dependency", "library-with-rules"
        )
        assertThat(diRuntimeResult.output).contains("Variant runtimeElements:")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test normal projects can consume libraries with rules`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            projectWithRules()
            subProject("code-to-check") {
                plugins {
                    id("java")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """implementation(project(":library-with-rules"))"""
                )
                src {
                    main {
                        exampleDeprecatedUsage()
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":library-with-rules:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)

        assertThat(result.task(":code-to-check:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `test proto integration`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            projectWithRules {
                dependencies("""implementation("com.netflix.nebula:archrules-deprecation:latest.release")""")
            }
            projectWithCodeUsingDeprecatedCode {
                plugins {
                    id("com.google.protobuf").version("0.9.6")
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    /**
     * creates a subproject which consumes libraries which should have the rules evaluated against it
     */
    fun TestProjectBuilder.projectWithCodeUsingDeprecatedCode(additionalConfig: ProjectBuilder.() -> Unit = {}) {
        subProject("code-to-check") {
            plugins {
                id("java")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                mavenCentral()
            }
            dependencies(
                """implementation(project(":library-with-rules"))"""
            )
            src {
                main {
                    exampleDeprecatedUsage()
                }
            }
            additionalConfig.invoke(this)
        }
    }

    fun TestProjectBuilder.emptyRuleProject(additionalConfig: ProjectBuilder.() -> Unit = {}) {
        subProject("library-with-rules") {
            // a library that contains production code and rules to go along with it
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.library")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            src {
                main {
                    exampleLibraryClass()
                }
                sourceSet("archRules") {

                }
            }
            additionalConfig.invoke(this)
        }
    }

    fun TestProjectBuilder.projectWithRules(additionalConfig: ProjectBuilder.() -> Unit = {}) {
        emptyRuleProject {
            src {
                sourceSet("archRules") {
                    exampleDeprecatedArchRule()
                }
            }
            additionalConfig.invoke(this)
        }
    }

    fun TestProjectBuilder.projectWithHighRules(additionalConfig: ProjectBuilder.() -> Unit = {}) {
        emptyRuleProject {
            src {
                sourceSet("archRules") {
                    exampleDeprecatedHighArchRule()
                }
            }
            additionalConfig.invoke(this)
        }
    }

    /**
     * checkstyle is an example of a task that depends on the classes task output of the archRules source set
     */
    @Test
    fun `test checkstyle integration`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            projectWithRules {
                plugins {
                    id("checkstyle")
                }
            }
        }
        projectDir.resolve("config/checkstyle/checkstyle.xml").apply {
            parentFile.mkdirs()
            createNewFile()
            writeText(
                //language=xml
                """<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
  "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
  "https://checkstyle.org/dtds/configuration_1_3.dtd">
  <module name="Checker">
  </module>
  """
            )
        }

        val result = runner.run("check", "--stacktrace")
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }
}
