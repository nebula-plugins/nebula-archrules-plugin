package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestKitAssertions.assertThat
import nebula.test.dsl.main
import nebula.test.dsl.plugins
import nebula.test.dsl.properties
import nebula.test.dsl.repositories
import nebula.test.dsl.sourceSet
import nebula.test.dsl.src
import nebula.test.dsl.subProject
import nebula.test.dsl.testProject
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * This test reproduces the issue fixed by https://github.com/TNG/ArchUnit/pull/1565/
 */
class ExternalPackageInfoTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `package rules work for external packages`() {
        val runner = testProject(projectDir) {
            properties {
                buildCache(true)
                configurationCache(true)
            }
            subProject("app") {
                plugins {
                    id("java")
                    id("com.netflix.nebula.archrules.runner")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """implementation(project(":lib"))"""
                )
                javaToolchain(17)
                src {
                    main {
                        java(
                            "Usage.java",
                            // language=java
                            """
import test.library.LibraryClass;
class Usage {
    LibraryClass libraryClass;
}
                        """
                        )
                    }
                }
            }
            subProject("lib") {
                plugins {
                    id("java-library")
                }
                repositories {
                    mavenCentral()
                }
                dependencies(
                    """api(project(":annotation"))"""
                )
                javaToolchain(17)
                src {
                    main {
                        java(
                            "test/library/LibraryClass.java", """
package test.library;
public class LibraryClass {
}
"""
                        )
                        java(
                            "test/library/package-info.java", """
@PackageAnnotation
package test.library;
import ann.PackageAnnotation;
"""
                        )
                    }
                }
            }
            subProject("annotation") {
                plugins {
                    id("java-library")
                    id("com.netflix.nebula.archrules.library")
                }
                repositories {
                    mavenCentral()
                }
                javaToolchain(17)
                dependencies("""archRulesImplementation("com.netflix.nebula:archrules-common:1.+")""")
                src {
                    main {
                        java(
                            "ann/PackageAnnotation.java",
                            // language=java
                            """
package ann;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PACKAGE)
public @interface PackageAnnotation {
}
"""
                        )
                    }
                    sourceSet("archRules") {
                        java(
                            "PackageAnnotationRule.java",
                            // language=java
                            """
import java.util.Collections;
import java.util.Map;
import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.netflix.nebula.archrules.common.JavaClass.Predicates.resideInAPackageThat;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;

public class PackageAnnotationRule implements ArchRulesService {
    public static final ArchRule RULE = ArchRuleDefinition.priority(Priority.HIGH)
            .noClasses()
            .should()
            .dependOnClassesThat(resideInAPackageThat(is(annotatedWith("ann.PackageAnnotation"))))
            .allowEmptyShould(true);
    @Override
    public Map<String, ArchRule> getRules() {
        return Collections.singletonMap("rule", RULE);
    }
}
"""
                        )
                    }
                }
            }
        }

        val result = runner.run("check")

        assertThat(result.task(":app:archRulesConsoleReport"))
            .`as`("archRules console report runs")
            .hasOutcome(TaskOutcome.SUCCESS)
        assertThat(result.output)
            .doesNotContain("(No failures)")
            .contains("rule  HIGH       (1 failures)")
    }
}
