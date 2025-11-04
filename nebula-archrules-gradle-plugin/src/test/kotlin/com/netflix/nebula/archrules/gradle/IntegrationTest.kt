package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.*
import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class IntegrationTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun test() {
        val runner = testProject(projectDir) {
            subProject("library-with-rules") {
                // a library that contains production code and rules to go along with it
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                src {
                    main {
                        java(
                            "com/example/library/LibraryClass.java",
                            //language=java
                            """
package com.example.library;
                            
public class LibraryClass {
    
}
                        """
                        )
                    }
                }
            }
            subProject("code-to-check") {
                // a project which consumes libraries which should have the rules evaluated against it
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                dependencies(
                    """implementation(project(":library-with-rules"))"""
                )
            }
        }

        val result = runner.run("check")

        assertThat(result.task(":library-with-rules:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result.task(":code-to-check:check"))
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
        assertThat(result)
            .hasNoMutableStateWarnings()
            .hasNoDeprecationWarnings()

        assertThat(projectDir.resolve("library-with-rules/build/libs/library-with-rules.jar"))
            .`as`("Library Jar is created")
            .exists()
    }
}