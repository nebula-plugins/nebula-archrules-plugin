package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.kotlin.dsl.findByType
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

    companion object {
        const val LOW_FAILURE_DETAILS =
            "Method <com.example.consumer.FailingCode.aMethod()> calls method <com.example.library.LibraryClass.deprecatedApi()> in (FailingCode.java"
        const val MEDIUM_FAILURE_DETAILS =
            "Method <com.example.consumer.FailingCode.forRemovalMethod()> calls method <com.example.library.LibraryClass.deprecatedForRemovalApi()> in (FailingCode.java"
        const val FILTERED_DETAILS_NOTE = "Note: In order to see details"
        const val LOW_PASSING_SUMMARY = "LOW        (No failures)"
    }

    fun TestProjectBuilder.setupConsumerProject(
        ruleDependency: Boolean = true,
        setupSources: Boolean = true,
        additionalConfig: nebula.test.dsl.ProjectBuilder.() -> Unit = {}
    ) {
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
            if (ruleDependency) {
                dependencies(
                    """archRules("com.netflix.nebula:archrules-deprecation:0.+")"""
                )
            }
            additionalConfig.invoke(this)
            if (setupSources) {
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
    }

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

    @Test
    fun `settings defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(ArchrulesRunnerPlugin::class.java)
        val extension = project.extensions.findByType<ArchrulesExtension>()!!
        assertThat(extension.consoleReportEnabled.get()).isTrue()
        assertThat(extension.sourceSetsToSkip.get()).containsExactly("archRulesTest")
        assertThat(extension.skipPassingSummaries.get()).isFalse()
        assertThat(extension.failureThreshold.isPresent).isFalse()
        assertThat(extension.consoleDetailsThreshold.get()).isEqualTo(Priority.MEDIUM)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks each sourceset`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject()
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
        assertThat(mainErrors).hasSize(3)

        val testReport = projectDir.resolve("build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(2)

        val jsonReport = projectDir.resolve("build/reports/archrules/report.json")
        assertThat(jsonReport)
            .`as`("json report created")
            .exists()

        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("Note: In order to see details of rules with priority less than MEDIUM,")
    }

    @Test
    fun `plugin checks each sourceset from its runtime`() {
        val runner = testProject(projectDir) {
            setupConsumerProject(ruleDependency = false) {
                dependencies(
                    """testImplementation("com.netflix.nebula:archrules-deprecation:0.+")"""
                )
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
        assertThat(testErrors).hasSize(2)
    }

    @Test
    fun `console report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject(setupSources = false) {
                rawBuildScript(
                    """
archRules {
    consoleReportEnabled = false
}
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .doesNotContain("ArchRule summary:")
            .doesNotContain("deprecated                     LOW        (1 failures)")
            .doesNotContain(MEDIUM_FAILURE_DETAILS)
            .doesNotContain(LOW_FAILURE_DETAILS)
            .doesNotContain(FILTERED_DETAILS_NOTE)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks additional sourcesets`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject(setupSources = false) {
                rawBuildScript("""sourceSets.create("custom")""")
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
            setupConsumerProject(setupSources = false) {
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
            .contains(LOW_PASSING_SUMMARY)
            .contains("MEDIUM     (No failures)")
    }

    @Test
    fun `passing summaries can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject(setupSources = false) {
                rawBuildScript(
                    """
archRules {
    skipPassingSummaries = true
}
"""
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
            properties {
                buildCache(true)
            }
            settings {
                name("consumer")
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
            .doesNotContain(LOW_PASSING_SUMMARY)
    }

    @Test
    fun `details threshold set to medium (default)`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    consoleDetailsThreshold("MEDIUM")
}
"""
                )
            }
            properties {
                buildCache(true)
            }
            settings {
                name("consumer")
            }
        }

        val result = runner.run("archRulesConsoleReport", "--stacktrace")

        assertThat(result.output)
            .`as`("only medium priority failure details are shown")
            .contains(MEDIUM_FAILURE_DETAILS)
            .contains(FILTERED_DETAILS_NOTE)
            .doesNotContain(LOW_FAILURE_DETAILS)
    }

    @Test
    fun `details threshold set to low`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    consoleDetailsThreshold("LOW")
}
"""
                )
            }
            properties {
                buildCache(true)
            }
            settings {
                name("consumer")
            }
        }

        val result = runner.run("archRulesConsoleReport", "--stacktrace")

        assertThat(result.output)
            .`as`("low priority failure details are shown")
            .contains(LOW_FAILURE_DETAILS)
            .doesNotContain(FILTERED_DETAILS_NOTE)
    }

    @Test
    fun `plugin skips archrules library test sourceset by default`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                plugins {
                    id("com.netflix.nebula.archrules.library")
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
            setupConsumerProject {
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

    @Test
    fun `can override priority of a rule`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    rule("deprecated") {
        priority("HIGH")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace", "-x", "test")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        // assert deprecated (LOW) is overridden
        val deprecatedResults = results.filter { it.rule.ruleName.equals("deprecated") }
        assertThat(deprecatedResults).hasSize(2)
        deprecatedResults.forEach { result ->
            assertThat(result.rule.priority).isEqualTo(Priority.HIGH)
        }

        // assert deprecatedForRemoval (MEDIUM), which is in the same class but not the same rule, is not overridden
        val deprecatedForRemovalResult = results.firstOrNull { it.rule.ruleName.equals("deprecatedForRemoval") }
        assertThat(deprecatedForRemovalResult).isNotNull
        assertThat(deprecatedForRemovalResult!!.rule.priority).isEqualTo(Priority.MEDIUM)
    }

    @Test
    fun `invalid priority string logs warning and does not override`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    rule("deprecatedForRemoval") {
        priority("NONE")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace", "-x", "test")

        assertThat(result.output)
            .contains("Invalid ArchRule priority 'NONE'")
            .contains("Must be one of the following (case-sensitive): HIGH, MEDIUM, LOW")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        // assert priority stays default
        val deprecationResult = results.firstOrNull { it.rule.ruleName.equals("deprecated") }
        assertThat(deprecationResult).isNotNull
        assertThat(deprecationResult!!.rule.priority).isEqualTo(Priority.LOW)
    }
}
