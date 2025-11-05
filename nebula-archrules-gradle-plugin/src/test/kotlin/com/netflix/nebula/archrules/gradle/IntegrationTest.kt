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
                repositories {
                    maven("https://netflixoss.jfrog.io/artifactory/gradle-plugins")
                    mavenCentral()
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
                    sourceSet("archRules") {
                        java(
                            "com/example/library/LibraryArchRules.java",
                            //language=java
                            """
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Map;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.targetOwner;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;

public class LibraryArchRules implements ArchRulesService {
    private final ArchRule noDeprecated =  ArchRuleDefinition.priority(Priority.LOW)
            .noClasses()
            .should().accessTargetWhere(targetOwner(annotatedWith(Deprecated.class)))
            .orShould().accessTargetWhere(target(annotatedWith(Deprecated.class)))
            .orShould().dependOnClassesThat().areAnnotatedWith(Deprecated.class)
            .allowEmptyShould(true)
            .as("No code should reference deprecated APIs")
            .because("usage of deprecated APIs introduces risk that future upgrades and migrations will be blocked");
            
    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of("deprecated", noDeprecated);
    }
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

        val result = runner.run("check", "compileArchRulesJava")

        assertThat(result.task(":library-with-rules:compileArchRulesJava"))
            .`as`("compile task runs for the archRules source set")
            .hasOutcome(TaskOutcome.SUCCESS, TaskOutcome.UP_TO_DATE)
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