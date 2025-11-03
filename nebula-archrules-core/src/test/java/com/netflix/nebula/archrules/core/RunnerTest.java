package com.netflix.nebula.archrules.core;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static org.assertj.core.api.Assertions.assertThat;

public class RunnerTest {
    @Test
    public void test_pass() {
        final var result = Runner.check(noDeprecatedRule, PassingClass.class);
        assertThat(result.hasViolation()).isFalse();
    }

    @Test
    public void test_fail() {
        final var result = Runner.check(noDeprecatedRule, FailingClass.class);
        assertThat(result.hasViolation()).isTrue();
    }

    @Test
    public void test_fail_and_pass() {
        final var result = Runner.check(noDeprecatedRule, PassingClass.class, FailingClass.class);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails()).hasSize(1);
    }

    static class PassingClass {
    }

    @Deprecated
    static class FailingClass {
    }

    private final ArchRule noDeprecatedRule = ArchRuleDefinition.classes().should()
            .notBeAnnotatedWith(Deprecated.class);
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
        final var result = Runner.check(smokeTestRule, SmokeTest.class);
        assertThat(result.hasViolation()).isFalse();
    }

    @Test
    public void test_smoke_fail() {
        final var result = Runner.check(smokeTestRule, SmokeTestFail.class);
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().getDetails())
                .contains(NoClassesMatchedEvent.NO_MATCH_MESSAGE);
    }
}
