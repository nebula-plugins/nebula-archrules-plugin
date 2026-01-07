package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
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
                        exampleDeprecatedArchRule()
                    }
                }
            }
            projectWithCodeUsingDeprecatedCode()
        }

        val result = runner.run("check", "--stacktrace") {
            withGradleVersion(gradleVersion.version)
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
            .hasOutcome(TaskOutcome.SKIPPED)

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
            subProject("library-with-rules") {
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    kotlin("jvm") version ("2.2.20")
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
                        exampleDeprecatedArchRule()
                    }
                }
            }
            projectWithCodeUsingDeprecatedCode()
        }

        val result = runner.run("check", "--stacktrace") {
            withGradleVersion(gradleVersion.version)
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
    }


    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `test normal projects can consume libraries with rules`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            subProject("library-with-rules") {
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    kotlin("jvm") version ("2.2.20")
                }
                repositories {
                    mavenCentral()
                }
                src {
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleDeprecatedArchRule()
                    }
                }
            }
            projectWithCodeUsingDeprecatedCode()
        }

        val result = runner.run("check", "--stacktrace") {
            withGradleVersion(gradleVersion.version)
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
    fun `test fail mode`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            subProject("library-with-rules") {
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
                src {
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleDeprecatedHighArchRule()
                    }
                }
            }
            projectWithCodeUsingDeprecatedCode(
                """
archRules {
    failureThreshold("HIGH")
}
"""
            )
        }

        val result = runner.runAndFail("check", "--stacktrace") {
            forwardOutput()
        }
        assertThat(result.task(":code-to-check:enforceArchRules"))
            .hasOutcome(TaskOutcome.FAILED)

        assertThat(result.output).contains("ArchRules failed: deprecated (HIGH)")

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    /**
     * creates a subproject which consumes libraries which should have the rules evaluated against it
     */
    fun TestProjectBuilder.projectWithCodeUsingDeprecatedCode(additionalScript: String? = null) {
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
            additionalScript?.let {
                rawBuildScript(it)
            }
        }
    }
}