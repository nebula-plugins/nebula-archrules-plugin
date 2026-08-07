package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestProjectBuilder
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.rootProject
import nebula.test.dsl.settings
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.testProject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ArchruleLibraryOutgoingVariantsTest {

    @TempDir
    lateinit var projectDir: File

    fun TestProjectBuilder.outgoingVariantSetup() {
        properties {
            buildCache(true)
            configurationCache(true)
            isolatedProjects(true)
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

    @Test
    fun `test testResultsElementsForArchRulesTest`() {
        val runner = testProject(projectDir) {
         outgoingVariantSetup()
        }

        val testResultsElementsForArchRulesTest =
            runner.run("outgoingVariants", "-Pversion=0.0.1", "--variant", "testResultsElementsForArchRulesTest")
        assertThat(testResultsElementsForArchRulesTest.output)
            .contains("- org.gradle.category         = verification")
            .contains("- org.gradle.testsuite.name   = archRulesTest")
            .contains("- org.gradle.verificationtype = test-results")
    }

    @Test
    fun `test archRulesRuntimeElements`() {
        val runner = testProject(projectDir) {
            outgoingVariantSetup()
        }

        val archRulesRuntimeElements =
            runner.run("outgoingVariants", "-Pversion=0.0.1", "--variant", "archRulesRuntimeElements")
        assertThat(archRulesRuntimeElements.output)
            .contains("Variant archRulesRuntimeElements")
            .contains("- com.netflix.nebula.archrules   = arch-rules")
            .contains("- org.gradle.usage               = arch-rules")
            .contains("build/libs/library-with-rules-0.0.1-arch-rules.jar")
            .contains("build/libs/library-with-rules-0.0.1.jar")
            .doesNotContain("Secondary Variant")
    }

    @Test
    fun `test archRulesApiElements`() {
        val runner = testProject(projectDir) {
            outgoingVariantSetup()
        }
        val archRulesApiElements =
            runner.run("outgoingVariants", "-Pversion=0.0.1", "--variant", "archRulesApiElements")
        assertThat(archRulesApiElements.output)
            .contains("There are no outgoing variants on project 'library-with-rules' named 'archRulesApiElements'")
    }

    @Test
    fun `test apiElements`() {
        val runner = testProject(projectDir) {
            outgoingVariantSetup()
        }

        val apiElements = runner.run("outgoingVariants", "-Pversion=0.0.1", "--variant", "apiElements")
        val primary = apiElements.output.substringBefore("Secondary Variants (*)")
        assertThat(primary)
            .contains("Variant apiElements")
            .doesNotContain("com.netflix.nebula.archrules")
            .contains("- org.gradle.usage               = java-api")
            .doesNotContain("arch-rules.jar")
            .contains("build/libs/library-with-rules-0.0.1.jar")

        val classes = apiElements.output
            .substringAfter("Secondary Variant classes")
            .substringBefore("Secondary Variant resources")

        assertThat(classes)
            .doesNotContain("com.netflix.nebula.archrules")
            .contains("- org.gradle.usage               = java-api")
            .doesNotContain("build/classes/java/archRules")
            .contains("build/classes/java/main")

        assertThat(apiElements.output).doesNotContain("Secondary Variant resources")
    }

    @Test
    fun `test runtimeElements`() {
        val runner = testProject(projectDir) {
            outgoingVariantSetup()
        }

        val runtimeElements = runner.run("outgoingVariants", "-Pversion=0.0.1", "--variant", "runtimeElements")
        val primary = runtimeElements.output.substringBefore("Secondary Variants (*)")
        assertThat(primary)
            .contains("Variant runtimeElements")
            .doesNotContain("com.netflix.nebula.archrules")
            .contains("- org.gradle.usage               = java-runtime")
            .doesNotContain("arch-rules.jar")
            .contains("build/libs/library-with-rules-0.0.1.jar")

        val classes = runtimeElements.output
            .substringAfter("Secondary Variant classes")
            .substringBefore("Secondary Variant resources")

        assertThat(classes)
            .doesNotContain("com.netflix.nebula.archrules")
            .contains("- org.gradle.usage               = java-runtime")
            .doesNotContain("build/classes/java/archRules")
            .contains("build/classes/java/main")

        val resources = runtimeElements.output
            .substringAfter("Secondary Variant resources")
        assertThat(resources)
            .doesNotContain("com.netflix.nebula.archrules")
            .contains("- org.gradle.usage               = java-runtime")
            .doesNotContain("build/resources/archRules")
            .contains("build/resources/main")
    }
}
