package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.BuildscriptLanguage
import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import net.lingala.zip4j.ZipFile
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ArchrulesLibraryJarTest {

    @TempDir
    lateinit var projectDir: File
    @Test

    fun `test archrules jar`() {
        val runner = testProject(projectDir, BuildscriptLanguage.GROOVY) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("rules-lib") {
                group("com.example")
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
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
        val result = runner.run("assemble", "-Pversion=0.0.1")

        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(result.task(":rules-lib:classes"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules-lib:jar"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules-lib:generateServicesRegistry"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)
        assertThat(result.task(":rules-lib:archRulesJar"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE, TaskOutcome.FROM_CACHE)

        val mainJar = projectDir.resolve("rules-lib/build/libs/rules-lib-0.0.1.jar")
        assertThat(mainJar).exists()
        val mainUnzipDest = projectDir.resolve("build/exploded/rules-lib")
        ZipFile(mainJar).extractAll(mainUnzipDest.absolutePath)
        assertThat(mainUnzipDest.list()).containsExactlyInAnyOrder("com", "META-INF")
        assertThat(mainUnzipDest.resolve("com/example/library").list())
            .containsExactlyInAnyOrder("LibraryClass.class")

        val archrulesJar = projectDir.resolve("rules-lib/build/libs/rules-lib-0.0.1-arch-rules.jar")
        assertThat(archrulesJar).exists()
        val unzipDest = projectDir.resolve("build/exploded/rules-lib-arch-rules")
        ZipFile(archrulesJar).extractAll(unzipDest.absolutePath)
        assertThat(unzipDest.list()).containsExactlyInAnyOrder("com", "META-INF")
        assertThat(unzipDest.resolve("com/example/library").list())
            .containsExactlyInAnyOrder("LibraryArchRules.class")
        assertThat(unzipDest.resolve("META-INF/services").list())
            .containsExactlyInAnyOrder("com.netflix.nebula.archrules.core.ArchRulesService")
    }
}
