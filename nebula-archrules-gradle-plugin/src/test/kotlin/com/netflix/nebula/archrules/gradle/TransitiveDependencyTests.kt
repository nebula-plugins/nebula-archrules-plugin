package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import net.javacrumbs.jsonunit.assertj.JsonAssertions.json
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * There are a lot of different scenarios to consider when dealing with transitive dependencies of rules libraries
 * this class contains tests for those
 */
class TransitiveDependencyTests {
    @TempDir
    lateinit var projectDir: File

    fun TestProjectBuilder.setupHelperProject() {
        subProject("helper") {
            group("com.example")
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.library")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            dependencies("""implementation("com.tngtech.archunit:archunit:1.4.1")""")
            src {
                main {
                    exampleHelperClass()
                }
            }
        }
    }

    fun TestProjectBuilder.setupDeprecatedRuleProject() {
        subProject("deprecated") {
            group("com.example")
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.library")
                id("maven-publish")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            declareMavenPublication()
            src {
                sourceSet("archRules") {
                    exampleDeprecatedArchRule()
                }
                sourceSet("archRulesTest") {
                    exampleTestForArchRule()
                }
            }
        }
    }

    fun TestProjectBuilder.setupProjectToRunIn(dependencyConfiguration: String) {
        subProject("runner") {
            group("com.example")
            plugins {
                id("java-library")
                id("com.netflix.nebula.archrules.runner")
            }
            repositories {
                maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                mavenCentral()
            }
            dependencies("""$dependencyConfiguration(project(":rules"))""")
        }
    }

    @Test
    fun `plugin sets up tests for rules with dependencies`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }
            subProject("rules") {
                group("com.example")
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                declareMavenPublication()
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                dependencies(
                    """archRulesImplementation(project(":helper"))""",
                    """archRulesTestImplementation("org.jspecify:jspecify:1.0.0")"""
                )
                src {
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleNullabilityArchRule() // rules that uses a helper from a dependency
                    }
                    sourceSet("archRulesTest") {
                        exampleTestForNullabilityArchRule()
                    }
                }
            }
            setupHelperProject()
            setupProjectToRunIn("implementation")
        }

        val result = runner.run(
            "check",
            "rules:generateMetadataFileForMavenPublication",
            "-Pversion=0.0.1",
            "--stacktrace"
        )

        assertThat(result.task(":rules:archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("rule is run")
            .contains("public classes should be @NullMarked")

        val moduleMetadata = projectDir.resolve("rules/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        assertThatArchRulesDependsOn(moduleMetadataJson, "com.example", "helper", "0.0.1")
    }

    @Test
    fun `plugin sets up tests for rules in archRules with helper dependencies`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }
            subProject("rules") {
                group("com.example")
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                declareMavenPublication()
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                dependencies(
                    """archRulesImplementation(project(":helper"))""",
                    """archRulesTestImplementation("org.jspecify:jspecify:1.0.0")"""
                )
                src {
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleNullabilityArchRule() // rules that uses a helper from a dependency
                    }
                    sourceSet("archRulesTest") {
                        exampleTestForNullabilityArchRule()
                    }
                }
            }
            setupHelperProject()
            setupProjectToRunIn("archRules")
        }

        val result = runner.run(
            "check",
            "rules:generateMetadataFileForMavenPublication",
            "-Pversion=0.0.1",
            "--stacktrace"
        )

        assertThat(result.task(":rules:archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
        assertThat(result.output)
            .`as`("rule is run")
            .contains("public classes should be @NullMarked")

        val moduleMetadata = projectDir.resolve("rules/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        assertThatArchRulesDependsOn(moduleMetadataJson, "com.example", "helper", "0.0.1")
    }

    @Test
    fun `test transitive rules in classpath`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }

            subProject("rules") {
                group("com.example")
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                declareMavenPublication()
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                dependencies("""archRulesImplementation(project(":deprecated"))""")
                src {
                    sourceSet("archRulesTest") {
                        exampleTestForArchRule()
                    }
                }
            }
            setupDeprecatedRuleProject()
            setupProjectToRunIn("implementation")
        }

        val result = runner.run(
            "check",
            "rules:generateMetadataFileForMavenPublication",
            "-Pversion=0.0.1",
            "--stacktrace"
        )
        assertThat(result.task(":rules:compileArchRulesTestJava"))
            .`as`("rules dependencies are exposed to archRulesTest")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules:archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("rule is run")
            .contains("deprecated  LOW")

        val moduleMetadata = projectDir.resolve("rules/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        println(moduleMetadataJson)
        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='runtimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1.jar")

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesApiElements')].files[0]")
            .`as`("apiElements is not produced for archRules")
            .isArray()
            .isEmpty()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1-arch-rules.jar")

        assertThatArchRulesDependsOn(moduleMetadataJson, "com.example", "deprecated", "0.0.1")
    }

    @Test
    fun `test transitive rules in archRules`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }

            subProject("rules") {
                group("com.example")
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                declareMavenPublication()
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                dependencies("""archRulesImplementation(project(":deprecated"))""")
                src {
                    sourceSet("archRulesTest") {
                        exampleTestForArchRule()
                    }
                }
            }
            setupDeprecatedRuleProject()
            setupProjectToRunIn("archRules")
        }

        val result = runner.run(
            "check",
            "rules:generateMetadataFileForMavenPublication",
            "-Pversion=0.0.1",
            "--stacktrace"
        )
        assertThat(result.task(":rules:compileArchRulesTestJava"))
            .`as`("rules dependencies are exposed to archRulesTest")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules:archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("rule is run")
            .contains("deprecated  LOW")

        val moduleMetadata = projectDir.resolve("rules/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        println(moduleMetadataJson)
        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='runtimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1.jar")

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesApiElements')].files[0]")
            .`as`("apiElements is not produced for archRules")
            .isArray()
            .isEmpty()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1-arch-rules.jar")

        assertThatArchRulesDependsOn(moduleMetadataJson, "com.example", "deprecated", "0.0.1")
    }

    @Test
    @Disabled("possible future functionality: putting rules sources in main for standalone rules libraries")
    fun `rules can be authored in main source sets`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }

            subProject("rules") {
                group("com.example")
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                declareMavenPublication()
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                dependencies("""implementation(project(":helper"))""")
                src {
                    main {
                        exampleNullabilityArchRule()
                    }
                    test {
                        exampleTestForNullabilityArchRule()
                    }
                }
            }
            setupHelperProject()
            setupProjectToRunIn("archRules")
        }

        val result = runner.run(
            "check",
            "rules:generateMetadataFileForMavenPublication",
            "-Pversion=0.0.1",
            "--stacktrace"
        )
        assertThat(result.task(":rules:compileTestJava"))
            .`as`("rules dependencies are exposed to archRulesTest")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules:archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.output)
            .`as`("rule is run")
            .contains("public classes should be @NullMarked")

        val moduleMetadata = projectDir.resolve("rules/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        println(moduleMetadataJson)
        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='runtimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1.jar")

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesApiElements')].files[0]")
            .`as`("apiElements is not produced for archRules")
            .isArray()
            .isEmpty()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "rules-0.0.1-arch-rules.jar")
        assertThatArchRulesDependsOn(moduleMetadataJson, "com.example", "helper", "0.0.1")
    }

    fun assertThatArchRulesDependsOn(moduleJson: String, group: String, module: String, version: String) {
        assertThatJson(moduleJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].dependencies[1]")
            .isArray
            .contains(
                json(
                    //language=json
                    """
{
  "group": "$group",
  "module": "$module",
  "version": {
    "requires": "$version"
  }
}
            """
                )
            )
    }
}