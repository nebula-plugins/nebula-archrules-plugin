package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectRunner
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.settings
import nebula.test.dsl.suites
import nebula.test.dsl.test
import nebula.test.dsl.testProject
import nebula.test.dsl.testing
import org.gradle.testkit.runner.BuildResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VariantSelectionTest {
    @TempDir
    lateinit var projectDir: File

    companion object {
        /**
         * This version was published with only the Usage attribute, and not the custom arch rules attribute
         */
        private const val RULES_USAGE_ONLY = """api("com.netflix.nebula:archrules-deprecation:1.0.1")"""

        /**
         * This version was published with Usage and custom attributes
         */
        private const val RULES_BOTH = """api("com.netflix.nebula:archrules-deprecation:0.11.2")"""
    }

    @Test
    fun `usage producer with runner consumer`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            rootProject {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(RULES_USAGE_ONLY)
            }
        }
        assertThat(runner.dependencyInsight("compileClasspath", "archrules-deprecation").output)
            .contains("Variant apiElements")

        assertThat(runner.dependencyInsight("mainArchRulesRuntime", "archrules-deprecation").output)
            .contains("Variant archRules")
        assertThat(runner.dependencyInsight("mainArchRulesRuntime", "nebula-archrules-core").output)
            .contains("Variant apiElements")
    }

    @Test
    fun `both producer with runner consumer`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            settings {
                name("library-with-rules")
            }
            rootProject {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(RULES_BOTH)
            }
        }
        assertThat(runner.dependencyInsight("compileClasspath", "archrules-deprecation").output)
            .contains("Variant apiElements")

        assertThat(runner.dependencyInsight("mainArchRulesRuntime", "archrules-deprecation").output)
            .contains("Variant archRules")
        assertThat(runner.dependencyInsight("mainArchRulesRuntime", "nebula-archrules-core").output)
            .contains("Variant apiElements")
    }

    @Test
    fun `usage producer with library consumer`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            rootProject {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(RULES_USAGE_ONLY)
                testing {
                    suites{
                        test {
                            useJUnitJupiter()
                        }
                    }
                }
            }
        }
        assertThat(runner.dependencyInsight("compileClasspath", "archrules-deprecation").output)
            .contains("Variant apiElements")
        assertThat(runner.dependencyInsight("testRuntimeClasspath", "junit-platform-engine").output)
            .contains("Variant runtimeElements")
        assertThat(runner.dependencyInsight("archRulesCompileClasspath", "nebula-archrules-core").output)
            .contains("Variant apiElements")
        assertThat(runner.dependencyInsight("archRulesCompileClasspath", "archrules-deprecation").output)
            .contains("Variant archRules")
        assertThat(runner.dependencyInsight("archRulesTestRuntimeClasspath", "junit-platform-engine").output)
            .contains("Variant runtimeElements")
    }

    @Test
    fun `both producer with library consumer`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            rootProject {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(RULES_BOTH)
                testing {
                    suites{
                        test {
                            useJUnitJupiter()
                        }
                    }
                }
            }
        }
        assertThat(runner.dependencyInsight("compileClasspath", "archrules-deprecation").output)
            .contains("Variant apiElements")
        assertThat(runner.dependencyInsight("testRuntimeClasspath", "junit-platform-engine").output)
            .contains("Variant runtimeElements")
        assertThat(runner.dependencyInsight("archRulesCompileClasspath", "nebula-archrules-core").output)
            .contains("Variant apiElements")
        assertThat(runner.dependencyInsight("archRulesCompileClasspath", "archrules-deprecation").output)
            .contains("Variant archRules")
        assertThat(runner.dependencyInsight("archRulesTestRuntimeClasspath", "junit-platform-engine").output)
            .contains("Variant runtimeElements")
    }

    private fun TestProjectRunner.dependencyInsight(configuration: String, dependency: String): BuildResult {
        return run("dependencyInsight", "--dependency", dependency, "--configuration", configuration)
    }
}
