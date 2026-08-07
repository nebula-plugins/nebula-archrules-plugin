package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class DependenciesIntegrationTest {

    @TempDir
    lateinit var projectDir: File

    fun TestProjectBuilder.dependenciesSetup(consumableConfigurationName: String? = null) {
        properties {
            buildCache(true)
            configurationCache(true)
            isolatedProjects(true)
        }
        subProject("runtime-library"){
            plugins {
                id("java-library")
            }
        }
        subProject("compile-library"){
            plugins {
                id("java-library")
            }
        }
        subProject("archrules-implementation-library"){
            plugins {
                id("java-library")
            }
        }
        subProject("api-library"){
            plugins {
                id("java-library")
                dependencies(
                    """implementation(project(":runtime-library"))""",
                    """api(project(":compile-library"))"""
                )
            }
        }
        subProject("library-with-rules") {
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.library")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            dependencies(
                """implementation(project(":runtime-library"))""",
                """api(project(":api-library"))""",
                """archRulesImplementation(project(":archrules-implementation-library"))""",
            )
            consumableConfigurationName?.also {
                printDeps(it)
            }
        }
        subProject("another-library-with-rules") {
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.library")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            dependencies(
                """implementation(project(":runtime-library"))""",
                """api(project(":api-library"))""",
                """archRulesImplementation(project(":library-with-rules"))""",
            )
            consumableConfigurationName?.also {
                printDeps(it)
            }
        }
        subProject("consumer"){
            plugins {
                id("java")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                mavenCentral() // needed to resolve reporting classpaths
            }
            dependencies("""implementation(project(":library-with-rules"))""")
        }
    }

    @Test
    fun `test library archRulesRuntimeClasspath`() {
        val runner = testProject(projectDir) {
            dependenciesSetup()
        }

        val archrulesImplementationLibrary = runner.run(
            ":library-with-rules:dependencyInsight",
            "--configuration", "archRulesRuntimeClasspath",
            "--dependency", "archrules-implementation-library"
        )
        assertThat(archrulesImplementationLibrary.output).contains("Variant apiElements")
    }

    @Test
    fun `test library archRulesRuntimeElements`() {
        val runner = testProject(projectDir) {
            dependenciesSetup("archRulesRuntimeElements")
        }

        val printDeps = runner.run(":library-with-rules:dependencies", "--configuration", "printDeps")
        assertThat(printDeps.output)
            .`as`("api dependencies are included transitively")
            .contains("api-library")
        assertThat(printDeps.output)
            .`as`("archrule implementation dependencies are included transitively")
            .contains("archrules-implementation-library")
        assertThat(printDeps.output)
            .`as`("implementation dependencies are not included transitively")
            .doesNotContain("runtime-library")
    }

    @Test
    fun `test consumer mainArchRulesRuntime`() {
        val runner = testProject(projectDir) {
            dependenciesSetup()
        }

        val libraryWithRules = runner.run(":consumer:dependencyInsight",
            "--configuration", "mainArchRulesRuntime",
            "--dependency", "library-with-rules")
        assertThat(libraryWithRules.output).contains("Variant archRulesRuntimeElements")

        val printDeps = runner.run(":consumer:dependencies", "--configuration", "mainArchRulesRuntime")
        assertThat(printDeps.output)
            .`as`("api dependencies are included transitively")
            .contains("compile-library")
        assertThat(printDeps.output)
            .`as`("implementation dependencies are not included transitively")
            .doesNotContain("runtime-library")
        assertThat(printDeps.output)
            .`as`("archrules implementation dependencies are included transitively")
            .contains("archrules-implementation-library")
    }

    @Test
    fun `test library consumer archRulesCompileClasspath`() {
        val runner = testProject(projectDir) {
            dependenciesSetup()
        }

        val libraryWithRules = runner.run(":another-library-with-rules:dependencyInsight",
            "--configuration", "archRulesCompileClasspath",
            "--dependency", "library-with-rules")
        assertThat(libraryWithRules.output)
            .contains("Variant archRulesRuntimeElements")
    }
}
