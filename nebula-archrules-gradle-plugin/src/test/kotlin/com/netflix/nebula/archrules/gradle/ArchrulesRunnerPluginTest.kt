package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.settings
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.test
import nebula.test.dsl.testProject
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream

class ArchrulesRunnerPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin registers archRules configuration`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(ArchrulesRunnerPlugin::class.java)
        val configuration = project.configurations.findByName("archRules")
        assertThat(configuration).isNotNull
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks each sourceset`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("consumer")
            }
            rootProject {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """archRules("com.netflix.nebula:archrules-deprecation:0.1.+")"""
                )
                src {
                    main {
                        exampleLibraryClass()
                        exampleDeprecatedUsage()
                    }
                    test {
                        exampleDeprecatedUsage("FailingCodeTest")
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test"){
            withGradleVersion(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":archRulesJsonReport"))
            .`as`("archRules json report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        assertThat(mainReport)
            .`as`("Main data created")
            .exists()
        val mainErrors = readDetails(mainReport)
        assertThat(mainErrors).hasSize(1)

        val testReport = projectDir.resolve("build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(1)

        val jsonReport = projectDir.resolve("build/reports/archrules/report.json")
        assertThat(jsonReport)
            .`as`("json report created")
            .exists()
    }

    @Test
    fun `plugin checks each sourceset from its runtime`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("consumer")
            }
            rootProject {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """testImplementation("com.netflix.nebula:archrules-deprecation:0.1.+")"""
                )
                src {
                    main {
                        exampleLibraryClass()
                        exampleDeprecatedUsage()
                    }
                    test {
                        exampleDeprecatedUsage("FailingCodeTest")
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        assertThat(mainReport)
            .`as`("rule not in main classpath, so not checked")
            .exists()
        val mainErrors = readDetails(mainReport)
        assertThat(mainErrors).isEmpty()

        val testReport = projectDir.resolve("build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(1)
    }

    fun readDetails(dataFile: File): List<RuleResult> {
        val list: MutableList<RuleResult> = mutableListOf()
        try {
            ObjectInputStream(FileInputStream(dataFile)).use { objectInputStream ->
                val numObjects = objectInputStream.readInt()
                repeat(numObjects) {
                    list.add(objectInputStream.readObject() as RuleResult)
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException(e)
        }
        return list
    }
}
