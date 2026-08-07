package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.TestProjectRunner
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.run
import nebula.test.dsl.runAndFail
import nebula.test.dsl.settings
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.test
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
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
        const val FILTERED_DETAILS_NOTE = "Note: In order to see details"
        const val LOW_PASSING_SUMMARY = "LOW        (No failures)"
        const val LIBRARY_ONLY_PROJECT = "library-only"
        const val RULES_ONLY_PROJECT = "rules-only"
        const val LIBRARY_WITH_RULES_PROJECT = "library-with-rules"
    }

    fun TestProjectBuilder.setupConsumerProject(additionalConfig: nebula.test.dsl.ProjectBuilder.() -> Unit = {}) {
        properties {
            buildCache(true)
            configurationCache(true)
        }
        settings {
            name("consumer")
        }
        subProject(RULES_ONLY_PROJECT) {
            libraryWithRulesProject()
            src {
                sourceSet("archRules") {
                    dontUseRules()
                }
            }
        }
        subProject(LIBRARY_ONLY_PROJECT) {
            libraryWithRulesProject()
            src {
                main {
                    dontUseAnnotation("Low")
                    dontUseAnnotation("Medium")
                    dontUseAnnotation("High")
                    dontUseApi("Low")
                    dontUseApi("Medium")
                    dontUseApi("High")
                }
            }
        }
        subProject(LIBRARY_WITH_RULES_PROJECT) {
            libraryWithRulesProject()
            src {
                main {
                    dontUseAnnotation("Low")
                    dontUseAnnotation("Medium")
                    dontUseAnnotation("High")
                    dontUseApi("Low")
                    dontUseApi("Medium")
                    dontUseApi("High")
                }
                sourceSet("archRules") {
                    dontUseRules()
                }
            }
        }
        subProject("consumer") {
            plugins {
                id("java")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                mavenCentral() // needed to resolve reporting classpaths
            }
            additionalConfig.invoke(this)
        }
    }

    @Test
    fun `plugin registers archRules configuration`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.netflix.nebula.archrules.runner")
        val configuration = project.configurations.findByName("archRules")
        assertThat(configuration).isNotNull
    }

    @Test
    fun `report inputs are correct`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.netflix.nebula.archrules.runner")
        val consoleReport = project.tasks.named<PrintConsoleReportTask>("archRulesConsoleReport")
        assertThat(consoleReport.get().dataFiles.files)
            .`as`("console report inputs are correct")
            .hasSize(2)
        val jsonReport = project.tasks.named<PrintJsonReportTask>("archRulesJsonReport")
        assertThat(jsonReport.get().dataFiles.files)
            .`as`("json report inputs are correct")
            .hasSize(2)
    }

    @Test
    fun `settings defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply("com.netflix.nebula.archrules.runner")
        val extension = project.extensions.findByType<ArchrulesExtension>()!!
        assertThat(extension.consoleReportEnabled.get()).isTrue()
        assertThat(extension.jsonReportEnabled.get()).isTrue()
        assertThat(extension.sourceSetsToSkip.get()).containsExactly("archRulesTest")
        assertThat(extension.skipPassingSummaries.get()).isFalse()
        assertThat(extension.failureThreshold.isPresent).isFalse()
        assertThat(extension.consoleDetailsThreshold.get()).isEqualTo(Priority.MEDIUM)
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks each sourceset`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":$RULES_ONLY_PROJECT"))""",
                    """implementation(project(":$LIBRARY_ONLY_PROJECT"))""",
                    """testImplementation(project(":$LIBRARY_ONLY_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Medium")
                    }
                    test {
                        dontUseUsage("Low")
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:archRulesJsonReport"))
            .`as`("archRules json report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:archRulesMarkdownReport"))
            .`as`("archRules markdown report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":consumer:archRulesGithubReport"))
            .`as`("github report is disabled by default")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result.task(":consumer:enforceArchRules"))
            .`as`("verification runs but does not fail the build")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        assertThat(mainReport)
            .`as`("Main data created")
            .exists()
        val mainErrors = readDetails(mainReport)
        assertThat(mainErrors).hasSize(3)

        val testReport = projectDir.resolve("consumer/build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(3)

        val jsonReport = projectDir.resolve("consumer/build/reports/archrules/report.json")
        assertThat(jsonReport)
            .`as`("json report created")
            .exists()

        val markdownReport = projectDir.resolve("consumer/build/reports/archrules/report.md")
        assertThat(markdownReport)
            .`as`("markdown report is created")
            .exists()

        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("Note: In order to see details of rules with priority less than MEDIUM,")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin produces outgoing variants for reports`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject()
        }

        val result = runner.run(":consumer:outgoingVariants", "--variant", "archRulesReportElements") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }
        assertThat(result.output.substringBefore("Secondary Variants (*)"))
            .contains("- org.gradle.category         = verification")
            .contains("- org.gradle.verificationtype = arch-rules")
            .contains("- build/reports/archrules/main.data (artifactType = binary)")
            .contains("- build/reports/archrules/test.data (artifactType = binary)")
    }

    @Test
    fun `plugin checks each sourceset from its runtime`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """testImplementation(project(":library-with-rules"))"""
                )
                src {
                    test {
                        dontUseUsage("Low")
                    }
                }
            }
        }

        val result = runner.run(":consumer:check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        assertThat(mainReport)
            .`as`("rule not in main classpath, so not checked")
            .exists()
        val mainErrors = readDetails(mainReport)
        assertThat(mainErrors).isEmpty()

        val testReport = projectDir.resolve("consumer/build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(3)
    }

    @Test
    fun `plugin checks each sourceset from its compile classpath`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """testCompileOnly(project(":library-with-rules"))"""
                )
                src {
                    test {
                        dontUseUsage("Low")
                    }
                }
            }
        }

        val result = runner.run(":consumer:check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        assertThat(mainReport)
            .`as`("rule not in main classpath, so not checked")
            .exists()
        val mainErrors = readDetails(mainReport)
        assertThat(mainErrors).isEmpty()

        val testReport = projectDir.resolve("consumer/build/reports/archrules/test.data")
        assertThat(testReport)
            .`as`("Test data created")
            .exists()
        val testErrors = readDetails(testReport)
        assertThat(testErrors).hasSize(3)
        assertThat(testErrors.first { it.rule.ruleName == "dont use Low" }.status() == RuleResultStatus.FAIL)
    }

    @Test
    fun `console report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies("""implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))""")
                src {
                    main {
                        dontUseUsage("High")
                    }
                }
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

        assertThat(result.task(":consumer:archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .doesNotContain("ArchRule summary:")
            .doesNotContain("failures)")
            .doesNotContain(FILTERED_DETAILS_NOTE)
    }

    @Test
    fun `json report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies("""archRules(project(":$RULES_ONLY_PROJECT"))""")
                rawBuildScript(
                    """
archRules {
    jsonReportEnabled = false
}
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:archRulesJsonReport"))
            .`as`("json report task is skipped")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val jsonReport = projectDir.resolve("consumer/build/reports/archrules/report.json")
        assertThat(jsonReport)
            .`as`("json report is not created")
            .doesNotExist()
    }

    @Test
    fun `markdown report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies("""archRules(project(":$RULES_ONLY_PROJECT"))""")
                rawBuildScript(
                    """
archRules {
    markdownReportEnabled = false
}
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:archRulesMarkdownReport"))
            .`as`("markdown report task is skipped")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val markdownReport = projectDir.resolve("build/reports/archrules/report.md")
        assertThat(markdownReport)
            .`as`("markdown report is not created")
            .doesNotExist()
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin checks additional sourcesets`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies("""archRules(project(":$RULES_ONLY_PROJECT"))""")
                rawBuildScript("""sourceSets.create("custom")""")
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":consumer:checkArchRulesCustom"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `passing summaries print by default`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":$RULES_ONLY_PROJECT"))""",
                    """implementation(project(":$LIBRARY_ONLY_PROJECT"))""",
                    """testImplementation(project(":$LIBRARY_ONLY_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("High")
                    }
                    test {
                        dontUseUsage("Medium")
                    }
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .contains("com.example.library.DontUseArchRules")
            .contains("dont use Low     LOW        (No failures)")
    }

    @Test
    fun `passing summaries can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
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

        assertThat(result.task(":consumer:archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .doesNotContain("com.netflix.nebula.archrules.deprecation.DeprecationRule")
            .doesNotContain(LOW_PASSING_SUMMARY)
    }

    @Test
    fun `details threshold set to medium (default)`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Low")
                        dontUseUsage("Medium")
                    }
                }
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
            .contains("Rule: dont use Medium Priority: MEDIUM")
            .contains(FILTERED_DETAILS_NOTE)
            .doesNotContain("Rule: dont use Low Priority: LOW")
    }

    @Test
    fun `details threshold set to low`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Low")
                    }
                }
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
            .contains("No code should reference DontUseLow APIs")
            .doesNotContain(FILTERED_DETAILS_NOTE)
    }

    @Test
    fun `plugin skips archrules library test sourceset by default`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":library-with-rules"))"""
                )
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesArchRulesTest"))
            .`as`("archRules run for test source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val archRulesTestReport = projectDir.resolve("consumer/build/reports/archrules/archRulesTest.data")
        assertThat(archRulesTestReport)
            .`as`("archRulesTestReport data not created")
            .doesNotExist()

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `plugin can skip configured source sets`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":library-with-rules"))"""
                )
                src {
                    test {
                        dontUseUsage("High")
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

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":consumer:checkArchRulesTest"))
            .`as`("tasks for skipped sources set still runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val archRulesTestReport = projectDir.resolve("consumer/build/reports/archrules/test.data")
        assertThat(archRulesTestReport)
            .`as`("skipped sources set data not created")
            .doesNotExist()

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
    fun `can override priority of a rule using rule name`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Low")
                    }
                }
                rawBuildScript(
                    """
archRules {
    ruleName("dont use Low") {
        priority("MEDIUM")
    }
}
"""
                )
            }
        }

        val result = runner.run(":consumer:checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        val lowRuleResults = results.filter { it.rule.ruleName == "dont use Low" }
        assertThat(lowRuleResults).hasSize(1)
        lowRuleResults.forEach { result ->
            assertThat(result.rule.priority).isEqualTo(Priority.MEDIUM)
        }
    }

    @Test
    fun `can override priority of a rule using rule class`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Low")
                    }
                }
                rawBuildScript(
                    """
archRules {
    ruleClass("com.example.library.DontUseArchRules") {
        priority("HIGH")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        val lowRuleResults = results.filter { it.rule.ruleName == "dont use Low" }
        assertThat(lowRuleResults).hasSize(1)
        lowRuleResults.forEach { result ->
            assertThat(result.rule.priority).isEqualTo(Priority.HIGH)
        }
    }

    @Test
    fun `rule level source set excludes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("High")
                    }
                }
                rawBuildScript(
                    """
archRules {
    ruleName("dont use High") {
        skipSourceSet("main")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        val deprecatedResults = results.filter { it.rule.ruleName == "dont use High" }
        assertThat(deprecatedResults).isEmpty()
    }

    @Test
    fun `ruleClass level source set excludes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":$RULES_ONLY_PROJECT"))"""
                )
                rawBuildScript(
                    """
archRules {
    ruleClass("com.example.library.DontUseArchRules") {
        skipSourceSet("main")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        assertThat(results).isEmpty()
    }

    @Test
    fun `test run rules using the rule-name command line param`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Low")
                        dontUseUsage("Medium")
                        dontUseUsage("High")
                    }
                }
            }
        }

        val result = runner.run(
            "archRulesConsoleReport",
            "--rule-name=\"dont use Low\"",
            "--rule-name=\"dont use Medium\"",
            "--stacktrace"
        )
        assertThat(result.task(":consumer:checkArchRulesMain"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.output)
            .contains("dont use Low")
            .contains("dont use Medium")
            .doesNotContain("dont use High")
    }

    @Test
    fun `invalid priority string logs warning and does not override`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":library-with-rules"))"""
                )
                rawBuildScript(
                    """
archRules {
    ruleName("dont use Low") {
        priority("NONE")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.output)
            .contains("Invalid ArchRule priority 'NONE'")
            .contains("Must be one of the following (case-sensitive): HIGH, MEDIUM, LOW")

        assertThat(result.task(":consumer:checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("consumer/build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        // assert priority stays default
        val deprecationResult = results.firstOrNull { it.rule.ruleName == "dont use Low" }
        assertThat(deprecationResult).isNotNull
        assertThat(deprecationResult!!.rule.priority).isEqualTo(Priority.LOW)
    }

    /**
     * This test is for making sure archrules will interop with other plugins that create and consume multiple variants
     */
    @Test
    fun `archrules runtime classpaths inherit sourceset runtime attributes`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("multi-variant-library") {
                plugins {
                    id("java-library")
                }
                javaToolchain(17)
                rawBuildScript(
                    // language=kotlin
                    """
val myAttribute = Attribute.of("com.example.my-attribute", String::class.java)
val otherSourceSet = java.sourceSets.create("other2")
val otherJar = project.tasks.register<Jar>("otherJar") {
    archiveClassifier.set("v2")
    from(otherSourceSet.output)
}
configurations.named("runtimeElements") {
    attributes {
        attribute(myAttribute, "v1")
    }
}

configurations.consumable("customRuntimeElements") {
    attributes {
        attribute(myAttribute, "v2")
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named( Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named( Category.LIBRARY))
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
}
artifacts {
    add("customRuntimeElements", otherJar)
}
"""
                )
            }
            subProject("consumer") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                dependencies(
                    """implementation(project(":multi-variant-library"))"""
                )
                // language=kotlin
                rawBuildScript(
                    """
val myAttribute = Attribute.of("com.example.my-attribute", String::class.java)
configurations.named("compileClasspath") {
    attributes {
        attribute(myAttribute, "v2")
    }
}
configurations.named("testCompileClasspath") {
    attributes {
        attribute(myAttribute, "v2")
    }
}
"""
                )
            }
        }

        val result = runner.run("archRulesConsoleReport", "--stacktrace")
        assertThat(result.task(":consumer:archRulesConsoleReport")).hasOutcome(TaskOutcome.SUCCESS)
    }

    private fun TestProjectRunner.outgoingVariant(name: String, customizer: GradleRunner.() -> Unit): String {
        return run("outgoingVariants", "--variant", name, "--stacktrace") {
            customizer.invoke(this)
        }.output.substringBefore("Secondary Variants (*)")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin verification`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
                src {
                    main {
                        dontUseUsage("Medium")
                    }
                }
                rawBuildScript(
                    """
archRules {
    failureThreshold("MEDIUM")
}
"""
                )
            }
        }

        val result = runner.runAndFail("check", "--stacktrace", "-x", "test") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }

        assertThat(result.task(":consumer:enforceArchRules"))
            .`as`("verification task fails")
            .hasOutcome(TaskOutcome.FAILED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .contains("ArchRules Critical Failure")
            .contains("Problems report is available at:")

        if (gradleVersion != SupportedGradleVersion.GRADLE_9_2) {
            assertThat(result.output)
                .`as`("enhanced problems output in newer gradle versions")
                .containsIgnoringCase("solution: Fix critical errors reported in Problems Report")
        }
    }

    @Test
    fun `plugin verification with stale skipped reports`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":library-with-rules"))"""
                )
                src {
                    main {
                        dontUseUsage("High")
                    }
                }
                rawBuildScript(
                    """
archRules {
    failureThreshold("MEDIUM")
}
"""
                )
            }
        }

        val result = runner.runAndFail("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":consumer:enforceArchRules"))
            .`as`("verification task fails")
            .hasOutcome(TaskOutcome.FAILED)
        projectDir.resolve("consumer/build.gradle.kts").appendText(
            """
archRules {
    skipSourceSet("main")
}
"""
        )
        val result2 = runner.run("check", "--stacktrace", "-x", "test")
        assertThat(result2.task(":consumer:enforceArchRules"))
            .`as`("verification task passes")
            .hasOutcome(TaskOutcome.SUCCESS)
    }

    @Test
    fun `archRulesRuntime configuration selects archRulesRuntimeElements variant`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
            }
        }
        val result = runner.run(
            ":consumer:dependencyInsight", "--stacktrace",
            "--configuration", "mainArchRulesRuntime",
            "--dependency", LIBRARY_WITH_RULES_PROJECT
        )
        assertThat(result.output)
            .doesNotContain("FAILED")
            .contains("Variant archRulesRuntimeElements")
    }

    @Test
    fun `archRulesRuntime configuration selects archRulesRuntimeElements variant from archRules`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """archRules(project(":$LIBRARY_WITH_RULES_PROJECT"))"""
                )
            }
        }
        val result = runner.run(
            ":consumer:dependencyInsight", "--stacktrace",
            "--configuration", "mainArchRulesRuntime",
            "--dependency", LIBRARY_WITH_RULES_PROJECT
        )
        assertThat(result.output)
            .doesNotContain("FAILED")
            .contains("Variant archRulesRuntimeElements")
    }

    @Test
    fun `archRulesRuntime configuration respects resolution rules`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """implementation("com.google.guava:guava")"""
                )
                rawBuildScript(
                    """
configurations.named("compileClasspath") {
    resolutionStrategy.dependencySubstitution {
        substitute(module("com.google.guava:guava")).using(module("com.google.guava:guava:21.0"))
    }
}
"""
                )
            }
        }

        val result = runner.run(
            ":consumer:dependencyInsight", "--stacktrace",
            "--configuration", "mainArchRulesRuntime",
            "--dependency", "guava"
        )
        assertThat(result.output)
            .doesNotContain("FAILED")
            .contains("com.google.guava:guava:21.0")
    }
}
