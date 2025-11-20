package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ArchrulesLibraryPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin registers library dependency`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(ArchrulesLibraryPlugin::class.java)
        val configuration = project.configurations.findByName("archRulesImplementation")
        assertThat(configuration).isNotNull
        val coreLibrary = configuration!!.dependencies
            .firstOrNull { it.group == "com.netflix.nebula" && it.name == "nebula-archrules-core" }
        assertThat(coreLibrary).isNotNull
        assertThat(coreLibrary!!.version).isEqualTo("latest.release")
    }

    @Test
    fun `plugin produces maven publication`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }
            rootProject {
                group("com.example")
                // a library that contains production code and rules to go along with it
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
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleDeprecatedArchRule()
                    }
                }
            }
        }

        val result = runner.run(
            "build",
            "archRulesJar",
            "generateMetadataFileForMavenPublication", // to test publication metadata without actually publishing,
            "-Pversion=0.0.1"
        )

        assertThat(result.task(":compileArchRulesJava"))
            .`as`("compile task runs for the archRules source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":archRulesJar"))
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.task(":generateServicesRegistry"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val serviceFile = projectDir.resolve("build/resources/archRules/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService")
        assertThat(serviceFile)
            .`as`("service file is created")
            .exists()
            .content().contains("com.example.library.LibraryArchRules")

        assertThat(projectDir.resolve("build/libs/library-with-rules-0.0.1.jar"))
            .`as`("Library Jar is created")
            .exists()
        assertThat(projectDir.resolve("build/libs/library-with-rules-0.0.1-arch-rules.jar"))
            .`as`("ArchRules Jar is created")
            .exists()

        val moduleMetadata = projectDir.resolve("build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()
        println(moduleMetadataJson)
        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='runtimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "library-with-rules-0.0.1.jar")

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesApiElements')].files[0]")
            .`as`("apiElements is not produced for archRules")
            .isArray()
            .isEmpty()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "library-with-rules-0.0.1-arch-rules.jar")
    }

    @Test
    fun `plugin produces proper outgoingVariants`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }
            rootProject {
                group("com.example")
                // a library that contains production code and rules to go along with it
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
                    main {
                        exampleLibraryClass()
                    }
                    sourceSet("archRules") {
                        exampleDeprecatedArchRule()
                    }
                }
            }
        }

        val result = runner.run("outgoingVariants", "-Pversion=0.0.1")
        assertThat(result.output)
            .contains("Variant archRulesRuntimeElements")
            .contains("Variant testResultsElementsForArchRulesTest")
            .doesNotContain("Variant archRulesApiElements")
    }

    @Test
    fun `plugin sets up tests for rules`() {
        val runner = testProject(projectDir) {
            properties {
                gradleCache(true)
            }
            settings {
                name("library-with-rules")
            }
            rootProject {
                group("com.example")
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
                    sourceSet("archRulesTest") {
                        exampleTestForArchRule()
                    }
                }
            }
        }

        val result = runner.run("check")

        assertThat(result.task(":archRulesTest"))
            .`as`("archRules test task runs")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }
}