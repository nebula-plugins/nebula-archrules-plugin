package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
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

    @Test
    fun `report inputs are correct`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(ArchrulesRunnerPlugin::class.java)
        val consoleReport = project.tasks.named<PrintConsoleReportTask>("archRulesConsoleReport")
        assertThat(consoleReport.get().dataFiles.get())
            .`as`("console report inputs are correct")
            .hasSize(2)
        val jsonReport = project.tasks.named<PrintJsonReportTask>("archRulesJsonReport")
        assertThat(jsonReport.get().dataFiles.get())
            .`as`("json report inputs are correct")
            .hasSize(2)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks each sourceset`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                property("org.gradle.configuration-cache", "true")
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

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradleVersion(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":archRulesJsonReport"))
            .`as`("archRules json report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":enforceArchRules"))
            .hasOutcome(TaskOutcome.SKIPPED)

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

        assertThat(result.output)
            .contains("com.netflix.nebula.archrules.deprecation.DeprecationRule")
            .contains("deprecated  LOW        (1 failures)")
    }

    @Test
    fun `plugin checks each sourceset from its runtime`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

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

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `console report can be disabled`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                rawBuildScript(
                    """
archRules {
    consoleReportEnabled = false
}
"""
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

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradleVersion(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .doesNotContain("ArchRule summary:")
            .doesNotContain("deprecated                     LOW        (1 failures)")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks additional sourcesets`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                rawBuildScript("""sourceSets.create("custom")""")
                dependencies(
                    """archRules("com.netflix.nebula:archrules-deprecation:0.1.+")"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradleVersion(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":checkArchRulesCustom"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `passing summaries print by default`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                    }
                    test {
                        exampleDeprecatedUsage("FailingCodeTest")
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .contains("com.netflix.nebula.archrules.deprecation.DeprecationRule")
            .contains("deprecated  LOW        (No failures)")
    }

    @Test
    fun `passing summaries can be disabled`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                    }
                    test {
                        exampleDeprecatedUsage("FailingCodeTest")
                    }
                }
                rawBuildScript(
                    """
archRules {
    skipPassingSummaries = true
}   
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .contains("com.netflix.nebula.archrules.deprecation.DeprecationRule")
            .doesNotContain("deprecated  LOW")
    }

    @Test
    fun `plugin skips archrules library test sourceset by default`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            settings {
                name("consumer")
            }
            rootProject {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
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
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":checkArchRulesArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `plugin can skip configured source sets`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                    """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
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
                rawBuildScript(
                    """
archRules {
    skipSourceSet("test")
}      
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
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
