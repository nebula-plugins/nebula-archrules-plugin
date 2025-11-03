package com.netflix.nebula.archrules.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;

public class Runner {
    public static EvaluationResult check(ArchRule rule, JavaClasses classesToCheck) {
        try {
            return rule.evaluate(classesToCheck);
        } catch (AssertionError e) {
            // evaluate the rule again with more leniency so we can get the priority
            final var result2 = rule.allowEmptyShould(true).evaluate(classesToCheck);
            if (result2.hasViolation()) {
                return result2;
            } else {
                ConditionEvents events = ConditionEvents.Factory.create();
                events.add(new NoClassesMatchedEvent());
                return new EvaluationResult(rule, events, result2.getPriority());
            }
        }
    }

    /**
     * Check a rule against some classes.
     * This can be invoked from the real Gradle plugin or unit tests for rules to ensure the same logic is observed there.
     */
    public static EvaluationResult check(ArchRule rule, Class<?>... classesToCheck) {
        final var classes = new ClassFileImporter()
                .importClasses(classesToCheck);
        return check(rule, classes);
    }
}
