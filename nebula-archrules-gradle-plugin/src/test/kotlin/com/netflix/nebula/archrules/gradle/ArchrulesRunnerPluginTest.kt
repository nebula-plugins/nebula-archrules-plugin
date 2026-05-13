package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.TestProjectRunner
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.runAndFail
import nebula.test.dsl.settings
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
            configurationCache(true)
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
            setupConsumerProject()
        }

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradle(gradleVersion.version)
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

        assertThat(result.task(":archRulesMarkdownReport"))
            .`as`("archRules markdown report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.task(":archRulesConsoleReport"))
            .`as`("archRules console report runs by default")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result.task(":enforceArchRules"))
            .`as`("verification runs but does not fail the build")
            .hasOutcome(TaskOutcome.SUCCESS)

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

        val markdownReport = projectDir.resolve("build/reports/archrules/report.md")
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

        val result = runner.outgoingVariant("archRulesReportElements") {
            withGradle(gradleVersion.version)
            forwardOutput()
        }
        assertThat(result)
            .contains("- org.gradle.category         = verification")
            .contains("- org.gradle.verificationtype = arch-rules")
            .contains("- build/reports/archrules/main.data (artifactType = binary)")
            .contains("- build/reports/archrules/test.data (artifactType = binary)")
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

    @Test
    fun `json report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject(setupSources = false) {
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

        assertThat(result.task(":archRulesJsonReport"))
            .`as`("json report task is skipped")
            .hasOutcome(TaskOutcome.SKIPPED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val jsonReport = projectDir.resolve("build/reports/archrules/report.json")
        assertThat(jsonReport)
            .`as`("json report is not created")
            .doesNotExist()
    }

    @Test
    fun `markdown report can be disabled`() {
        val runner = testProject(projectDir) {
            setupConsumerProject(setupSources = false) {
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

        assertThat(result.task(":archRulesMarkdownReport"))
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
            setupConsumerProject(setupSources = false) {
                rawBuildScript("""sourceSets.create("custom")""")
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test") {
            withGradle(gradleVersion.version)
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
            .doesNotContain("com.netflix.nebula.archrules.deprecation.DeprecationRule")
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
    fun `can override priority of a rule using both rule class and name`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    ruleClass("com.netflix.nebula.archrules.deprecation") {
        priority("HIGH")
    }
    ruleName("deprecated") {
        priority("MEDIUM")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        val deprecatedForRemovalResults = results.filter { it.rule.ruleName == "deprecatedForRemoval" }
        assertThat(deprecatedForRemovalResults).hasSize(1)
        deprecatedForRemovalResults.forEach { result ->
            assertThat(result.rule.priority).isEqualTo(Priority.HIGH)
        }

        val deprecatedResults = results.filter { it.rule.ruleName == "deprecated" }
        assertThat(deprecatedResults).hasSize(2)
        deprecatedResults.forEach { result ->
            assertThat(result.rule.priority).isEqualTo(Priority.MEDIUM)
        }
    }

    @Test
    fun `rule level source set excludes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    ruleName("deprecated") {
        skipSourceSet("main")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace", "--info")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        val deprecatedForRemovalResults = results.filter { it.rule.ruleName == "deprecatedForRemoval" }
        assertThat(deprecatedForRemovalResults).hasSize(1)

        val deprecatedResults = results.filter { it.rule.ruleName == "deprecated" }
        assertThat(deprecatedResults).isEmpty()
    }

    @Test
    fun `ruleClass level source set excludes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    ruleClass("com.netflix.nebula.archrules.deprecation") {
        skipSourceSet("main")
    }
}
"""
                )
            }
        }

        val result = runner.run("checkArchRulesMain", "--stacktrace")

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        assertThat(results).isEmpty()
    }

    @Test
    fun `ruleName level source set includes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies("""archRules("com.netflix.nebula:archrules-nullability:0.+")""")
            }
        }

        val result = runner.run(
            "archRulesConsoleReport",
            "--rule-name=no Optional class fields",
            "--rule-name=deprecated",
            "--stacktrace"
        )
        assertThat(result.task(":checkArchRulesMain")).hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        assertThat(result.output)
            .contains("deprecated")
            .contains("no Optional class fields")
            .doesNotContain("deprecatedForRemoval")
    }

    @Test
    fun `ruleClass level source set includes`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                dependencies(
                    """
                    archRules("com.netflix.nebula:archrules-nullability:0.+")
                    archRules("com.netflix.nebula:archrules-joda:0.+")
                """
                )
            }
        }

        val result = runner.run(
            "archRulesConsoleReport",
            "--rule-class=com.netflix.nebula.archrules.nullability",
            "--rule-name=jodaRule",
            "--stacktrace"
        )
        assertThat(result.task(":checkArchRulesMain")).hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val nullabilityRuleNames = readDetails(mainReport)
            .filter { it.rule.ruleClass().startsWith("com.netflix.nebula.archrules.nullability") }
            .map { it.rule.ruleName() }
            .distinct()

        assertThat(nullabilityRuleNames).isNotEmpty()
        nullabilityRuleNames.forEach { ruleName ->
            assertThat(result.output).contains(ruleName)
        }
        assertThat(result.output).contains("jodaRule")
        assertThat(result.output).doesNotContain("com.netflix.nebula.archrules.deprecation")
    }

    @Test
    fun `invalid priority string logs warning and does not override`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    ruleName("deprecatedForRemoval") {
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

        assertThat(result.task(":checkArchRulesMain"))
            .`as`("archRules run for main source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val mainReport = projectDir.resolve("build/reports/archrules/main.data")
        val results = readDetails(mainReport)

        // assert priority stays default
        val deprecationResult = results.firstOrNull { it.rule.ruleName == "deprecated" }
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
configurations.named("runtimeClasspath") {
    attributes {
        attribute(myAttribute, "v2")
    }
}
configurations.named("testRuntimeClasspath") {
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

        assertThat(result.task(":enforceArchRules"))
            .`as`("verification task fails")
            .hasOutcome(TaskOutcome.FAILED)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .contains("ArchRules Critical Failure")
            .contains("Problems report is available at:")

        if (gradleVersion != SupportedGradleVersion.GRADLE_9_1) {
            assertThat(result.output)
                .`as`("enhanced problems output in newer gradle versions")
                .contains("Solution: Fix critical errors reported in Problems Report")
        }
    }
}
