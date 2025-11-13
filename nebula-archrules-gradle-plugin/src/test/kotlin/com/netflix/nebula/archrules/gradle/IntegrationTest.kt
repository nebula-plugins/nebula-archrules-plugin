package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
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
                gradleCache(true)
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
            subProject("code-to-check") {
                // a project which consumes libraries which should have the rules evaluated against it
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
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

        val serviceFile = projectDir
                .resolve("library-with-rules/src/archRules/resources/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService")
        serviceFile.parentFile.mkdirs()
        serviceFile.writeText("com.example.library.LibraryArchRules")

        val result = runner.run("check") {
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
}