package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.ProjectBuilder
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.settings
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.test
import nebula.test.dsl.testProject
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArchrulesRunnerPluginGithubTest {
    @TempDir
    lateinit var projectDir: File

    fun TestProjectBuilder.setupConsumerProject(additionalConfig: ProjectBuilder.() -> Unit = {}) {
        properties {
            buildCache(true)
            configurationCache(true)
        }
        settings {
            name("consumer")
        }
        rootProject {
            consumerProject(additionalConfig)
        }
    }

    fun ProjectBuilder.consumerProject(additionalConfig: ProjectBuilder.() -> Unit = {}) {
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
        additionalConfig.invoke(this)
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

    @Test
    fun `github report can be enabled via extension`() {
        val runner = testProject(projectDir) {
            setupConsumerProject {
                rawBuildScript(
                    """
archRules {
    githubReportEnabled = true
}
"""
                )
            }
        }

        val result = runner.run("check", "--stacktrace", "-x", "test")

        assertThat(result.task(":archRulesGithubReport"))
            .`as`("github report task runs")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("::notice file=src/main/java/com/example/consumer/FailingCode.java")
        val githubReport = projectDir.resolve("build/reports/archrules/github-annotations.json")
        assertThat(githubReport)
            .`as`("github report file created")
            .exists()
            .content()
            .startsWith("""{"annotations":[{"path":"src/main/java/com/example/consumer/FailingCode.java","annotation_level":"warning","title":"deprecatedForRemoval","message"""")
    }

    @Test
    fun `github report can be enabled via property`() {
        val runner = testProject(projectDir) {
            setupConsumerProject()
        }

        val result = runner.run("check", "--stacktrace", "-x", "test", "-Parchrules.github.enabled=true")

        assertThat(result.task(":archRulesGithubReport"))
            .`as`("github report task runs")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("::notice file=src/main/java/com/example/consumer/FailingCode.java")
        val githubReport = projectDir.resolve("build/reports/archrules/github-annotations.json")
        assertThat(githubReport)
            .`as`("github report file created")
            .exists()
    }

    @Test
    fun `github report can be enabled via direct request`() {
        val runner = testProject(projectDir) {
            setupConsumerProject()
        }

        val result = runner.run("archRulesGithubReport", "--stacktrace")

        assertThat(result.task(":archRulesGithubReport"))
            .`as`("github report task runs")
            .hasOutcome(TaskOutcome.SUCCESS)

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("::notice file=src/main/java/com/example/consumer/FailingCode.java")
        val githubReport = projectDir.resolve("build/reports/archrules/github-annotations.json")
        assertThat(githubReport)
            .`as`("github report file created")
            .exists()
    }

    @Test
    fun `github report can be enabled for all subprojects via unqualified direct request`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            settings {
                name("consumer")
            }
            subProject("sub1") {
                consumerProject()
            }
            subProject("sub2") {
                consumerProject()
            }
        }

        val result = runner.run("archRulesGithubReport", "--stacktrace")

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.task(":sub1:archRulesGithubReport"))
            .`as`("github report task for sub1 runs")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":sub2:archRulesGithubReport"))
            .`as`("github report task for sub2 runs")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.output)
            .`as`("filtered details message is printed")
            .contains("::notice file=sub1/src/main/java/com/example/consumer/FailingCode.java")
        val githubReport = projectDir.resolve("sub1/build/reports/archrules/github-annotations.json")
        assertThat(githubReport)
            .`as`("github report file created")
            .exists()
            .content()
            .startsWith("""{"annotations":[{"path":"sub1/src/main/java/com/example/consumer/FailingCode.java","annotation_level":"warning","title":"deprecatedForRemoval","message"""")
    }
}
