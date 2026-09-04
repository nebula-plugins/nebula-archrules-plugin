package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.dependencies
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.run
import nebula.test.dsl.settings
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import nebula.test.dsl.withGradle
import net.javacrumbs.jsonunit.assertj.JsonAssertions.json
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

internal class ArchrulesLibraryPluginTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `plugin registers library dependency`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java-library")
        project.plugins.apply("com.netflix.nebula.archrules.library")
        val configuration = project.configurations.findByName("archRulesImplementation")
        assertThat(configuration).isNotNull
        val coreLibrary = configuration!!.dependencies
            .firstOrNull { it.group == "com.netflix.nebula" && it.name == "nebula-archrules-core" }
        assertThat(coreLibrary).isNotNull
        assertThat(coreLibrary!!.version).isEqualTo("latest.release")
    }

    @ParameterizedTest
    @EnumSource(SupportedGradleVersion::class)
    fun `plugin produces maven publication`(gradleVersion: SupportedGradleVersion) {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
            "--stacktrace",
            "build",
            "archRulesJar",
            "generateMetadataFileForMavenPublication", // to test publication metadata without actually publishing,
            "-Pversion=0.0.1"
        ){
            withGradle(gradleVersion .version)
        }

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

        val serviceFile =
            projectDir.resolve("build/resources/archRules/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService")
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
            .inPath("$.variants[?(@.name=='archRulesApiElements')]")
            .`as`("apiElements is produced for archRules")
            .isArray()
            .isEmpty()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].files[0]")
            .isArray
            .first().isObject
            .containsEntry("name", "library-with-rules-0.0.1-arch-rules.jar")
    }

    @Test
    fun `main api dependencies are included in archRules`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                dependencies("""api("com.google.guava:guava:33.5.0-jre")""")
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

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val moduleMetadata = projectDir.resolve("build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].dependencies[1]")
            .isArray
            .contains(
                json(
                    //language=json
                    """
{
  "group": "com.google.guava",
  "module": "guava",
  "version": {
    "requires": "33.5.0-jre"
  }
}
            """
                )
            )
    }

    @Test
    fun `archRules implementation dependencies are included in archRules`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            settings {
                name("library-with-rules")
            }
            subProject("helper"){
                group("com.example")
                plugins {
                    id("java-library")
                    id("maven-publish")
                }
                declareMavenPublication()
            }
            subProject("lib") {
                group("com.example")
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                    id("maven-publish")
                }
                repositories {
                    nebulaOss()
                }
                declareMavenPublication()
                dependencies {
                    add("archRulesImplementation", project(":helper"))
                }
            }
        }

        val result = runner.run("lib:generateMetadataFileForMavenPublication", "-Pversion=0.0.1")

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        val moduleMetadata = projectDir.resolve("lib/build/publications/maven/module.json")
        assertThat(moduleMetadata)
            .`as`("Gradle Module Metadata is created")
            .exists()

        val moduleMetadataJson = moduleMetadata.readText()

        assertThatJson(moduleMetadataJson)
            .inPath("$.variants[?(@.name=='archRulesRuntimeElements')].dependencies[1]")
            .isArray
            .contains(
                json(
                    //language=json
                    """
{
  "group": "com.example",
  "module": "helper",
  "version": {
    "requires": "0.0.1"
  }
}
            """
                )
            )
    }


    @Test
    fun `plugin sets up tests for rules`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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

    @Test
    fun `main code is included in archRulesTest`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
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
                dependencies("""implementation("com.google.guava:guava:33.5.0-jre")""")
                src {
                    main {
                        dontUseAnnotation()
                        dontUseAnnotation("Low")
                    }
                    sourceSet("archRules") {
                        dontUseRules()
                    }
                    sourceSet("archRulesTest") {
                        testForDontUseRule()
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

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()
    }

    @Test
    fun `test generateServicesRegistry`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
                isolatedProjects(true)
            }
            rootProject {
                libraryWithRulesProject()
                src {
                    sourceSet("archRules") {
                        exampleDeprecatedArchRule()
                    }
                }
            }
        }
        val result = runner.run("generateServicesRegistry")
        assertThat(result.task(":generateServicesRegistry"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)
        val serviceRegistryDir = projectDir.resolve("build/resources/archRules/META-INF/services")
        assertThat(serviceRegistryDir.list()).containsExactly("com.netflix.nebula.archrules.core.ArchRulesService")
        assertThat(serviceRegistryDir.resolve("com.netflix.nebula.archrules.core.ArchRulesService"))
            .exists()
            .content().contains("com.example.library.LibraryArchRules")
    }

    @Test
    fun `test generateRulesDocumentation task`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
            }
            subProject("common") {
                plugins {
                    id("java-library")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """
                    api("com.tngtech.archunit:archunit:1.+")
                """.trimIndent()
                )
                src {
                    main {
                        commonPredicateHelpers()
                    }
                }
            }
            subProject("archrules-library") {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
                }
                src {
                    sourceSet("archRules") {
                        dontUseRules()
                        exampleDeprecatedHighArchRule()
                        kotlinDeprecatedRuleUsingPredicateHelper()
                    }
                }
                dependencies(
                    """
                    archRulesImplementation("com.netflix.nebula:archrules-nullability:0.+")
                    archRulesImplementation(project(":common"))
                """.trimIndent()
                )
            }
        }

        val result = runner.run(":archrules-library:generateRulesDocumentation", "--stacktrace") {
            forwardOutput()
        }

        assertThat(result.task(":archrules-library:generateRulesDocumentation"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE)

        val docsFile = projectDir.resolve("archrules-library/build/docs/archrules.md")
        assertThat(docsFile).exists()

        assertThat(docsFile.readText())
            .contains("# ArchRules Documentation")
            .contains("List of all archrules defined in `archrules-library`.")
            .contains(
                "## Class: `com.example.library.DontUseArchRules`\n" +
                    "\n" +
                    "### dont use"
            )
            .contains(
                "## deprecated\n" +
                    "\n" +
                    "**Description:** No code should reference deprecated APIs, because usage of deprecated APIs introduces risk that future upgrades and migrations will be blocked\n" +
                    "\n" +
                    "**Priority:** HIGH"
            )
            .contains("## kotlinDeprecated")
            .doesNotContain("## public classes should be @NullMarked")
    }
}
