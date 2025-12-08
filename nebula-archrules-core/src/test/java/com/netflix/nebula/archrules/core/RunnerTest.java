package com.netflix.nebula.archrules.core;

import com.netflix.nebula.archrules.testpackage.TestDeprecated;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static org.assertj.core.api.Assertions.assertThat;

public class RunnerTest {
    private final ArchRule noDeprecatedRule = ArchRuleDefinition.classes().should()
            .notBeAnnotatedWith(Deprecated.class);

    @Test
    public void test_pass() {
        final EvaluationResult result = Runner.check(noDeprecatedRule, PassingClass.class);
        assertThat(result.hasViolation()).isFalse();
    }

    @Test
    public void test_fail() {
        final EvaluationResult result = Runner.check(noDeprecatedRule, FailingClass.class);
        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    public void test_fail_and_pass() {
        final EvaluationResult result = Runner.check(noDeprecatedRule, PassingClass.class, FailingClass.class);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(1);
    }

    static class PassingClass {
    }

    @Deprecated
    static class FailingClass {
    }

    private final ArchRule smokeTestRule = ArchRuleDefinition.priority(Priority.MEDIUM)
            .classes().that(simpleName("SmokeTest"))
            .should().beAnnotatedWith(Deprecated.class)
            .allowEmptyShould(false);

    static class SmokeTestFail {
    }

    @Deprecated
    static class SmokeTest {
    }

    @Test
    public void test_smoke_pass() {
        final EvaluationResult result = Runner.check(smokeTestRule, SmokeTest.class);
        assertThat(result.hasViolation()).isFalse();
    }

    @Test
    public void test_smoke_fail() {
        final EvaluationResult result = Runner.check(smokeTestRule, SmokeTestFail.class);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .contains(NoClassesMatchedEvent.NO_MATCH_MESSAGE);
    }

    private final ArchCondition<JavaClass> notBeInDeprecatedPackage =
            new ArchCondition<JavaClass>("not be in a package marked with @Deprecated") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean isInDeprecatedPackage = javaClass.getPackage().getPackageInfo().isAnnotatedWith(Deprecated.class);
                    if (isInDeprecatedPackage) {
                        String message = String.format("Class %s is in a package marked with @Deprecated", javaClass.getName());
                        events.add(SimpleConditionEvent.violated(javaClass, message));
                    }
                }
            };
    private final ArchRule noDeprecatedPackageRule = ArchRuleDefinition
            .classes().should(notBeInDeprecatedPackage);

    @Test
    public void test_package_deprecated_rule() {
        final EvaluationResult result = Runner.check(noDeprecatedPackageRule, TestDeprecated.class);
        assertThat(result.hasViolation()).isTrue();
    }

}
