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
    public static void newApi() {
    }
    
    @Deprecated
    public static void deprecatedApi() {
    }
}
"""
    )
}

fun SourceSetBuilder.exampleHelperClass() {
    java(
        "com/example/library/HaveNoTests.java",
        //language=java
        """
package com.example.library;
                            
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

public class HaveNoTests extends DescribedPredicate<JavaClass> {
    public HaveNoTests() {
        super("have no tests");
    }

    @Override
    public boolean test(JavaClass javaClass) {
        return javaClass.getMembers().stream()
                .noneMatch(it -> it.isAnnotatedWith("org.junit.jupiter.api.Test"));
    }
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
    public static final ArchRule noDeprecated = ArchRuleDefinition.priority(Priority.LOW)
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

fun SourceSetBuilder.exampleNullabilityArchRule() {
    java(
        "com/example/library/NullabilityArchRules.java",
        //language=java
        """
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.HasModifiers;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.util.Map;
import static com.tngtech.archunit.lang.conditions.ArchConditions.fullyQualifiedName;

public class NullabilityArchRules implements ArchRulesService {
    public static final ArchRule PUBLIC_CLASSES_SHOULD_BE_NULL_MARKED = ArchRuleDefinition.priority(Priority.MEDIUM)
            .classes().that()
            .areTopLevelClasses()
            .and().arePublic()
            .and().containAnyMembersThat(HasModifiers.Predicates.modifier(JavaModifier.PUBLIC))
            .and(new HaveNoTests())
            .and().areNotAnnotatedWith("kotlin.Metadata")
            .should().beAnnotatedWith("org.jspecify.annotations.NullMarked")
            .allowEmptyShould(true)
            .because("public classes should be null marked");
            
    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of("public classes should be @NullMarked", PUBLIC_CLASSES_SHOULD_BE_NULL_MARKED);
    }
}
"""
    )
}

fun SourceSetBuilder.exampleDeprecatedUsage(className: String = "FailingCode") {
    java(
        "com/example/consumer/$className.java",
        //language=java
        """
package com.example.consumer;

import com.example.library.LibraryClass;

class $className {
    public void aMethod() {
        LibraryClass.deprecatedApi();
    }
}
"""
    )
}

fun SourceSetBuilder.exampleTestForArchRule() {
    java(
        "com/example/library/LibraryArchRulesTest.java",
        //language=java
        """
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class LibraryArchRulesTest {
    @Deprecated
    static void deprecatedMethod(){
    }
    static class PassingCode {
        public void aMethod() {
        }
    }     
    static class FailingCode {
        public void aMethod() {
            deprecatedMethod();
        }
    }
    
    @Test
    public void test_pass() {
        EvaluationResult result = Runner.check(LibraryArchRules.noDeprecated, PassingCode.class);
        Assertions.assertFalse(result.hasViolation());
    }
        
    @Test
    public void test_fail() {
        EvaluationResult result = Runner.check(LibraryArchRules.noDeprecated, FailingCode.class);
        Assertions.assertTrue(result.hasViolation());
    }
}
"""
    )
}

fun SourceSetBuilder.exampleTestForNullabilityArchRule() {
    java("com/example/library/FailingCode.java",
        //language=java
        """
package com.example.library;
public class FailingCode {
    public void aMethod() {
    }
}
        """)
    java("com/example/library/PassingCode.java",
        //language=java
        """
package com.example.library;
import org.jspecify.annotations.NullMarked;
@NullMarked
public class PassingCode {
    public void aMethod() {
    }
}
        """)
    java(
        "com/example/library/NullabilityArchRulesTest.java",
        //language=java
        """
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class NullabilityArchRulesTest {

    @Test
    public void test_pass() {
        EvaluationResult result = Runner.check(NullabilityArchRules.PUBLIC_CLASSES_SHOULD_BE_NULL_MARKED, PassingCode.class);
        Assertions.assertFalse(result.hasViolation());
    }
        
    @Test
    public void test_fail() {
        EvaluationResult result = Runner.check(NullabilityArchRules.PUBLIC_CLASSES_SHOULD_BE_NULL_MARKED, FailingCode.class);
        Assertions.assertTrue(result.hasViolation());
    }
}
"""
    )
}