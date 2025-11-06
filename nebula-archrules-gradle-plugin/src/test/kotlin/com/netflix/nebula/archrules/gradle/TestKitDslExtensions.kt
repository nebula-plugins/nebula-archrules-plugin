package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.ProjectBuilder
import nebula.test.dsl.SourceSetBuilder

fun ProjectBuilder.declareMavenPublication() {
    //language=kotlin
    rawBuildScript(
        """
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
"""
    )
}

fun SourceSetBuilder.exampleLibraryClass() {
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

fun SourceSetBuilder.exampleDeprecatedArchRule() {
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