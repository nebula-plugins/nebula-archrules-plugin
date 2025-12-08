package com.netflix.nebula.archrules.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.core.importer.Locations;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class Runner {
    /**
     * Check a rule against some classes.
     * This can be invoked from the real Gradle plugin or unit tests for rules to ensure the same logic is observed there.
     *
     * @param rule           the rule to run
     * @param classesToCheck the classes to run the rule against
     * @return the result, which contains information about failure
     */
    public static EvaluationResult check(ArchRule rule, JavaClasses classesToCheck) {
        try {
            return rule.evaluate(classesToCheck);
        } catch (AssertionError e) {
            // evaluate the rule again with more leniency so we can get the priority
            final EvaluationResult result2 = rule.allowEmptyShould(true).evaluate(classesToCheck);
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
     * Check a rule against some classes and their package-info
     * This intended to be used from unit tests for rules to ensure the same logic is observed there.
     *
     * @param rule           the rule to run
     * @param classesToCheck the classes to run the rule against
     * @return the result, which contains information about failure
     */
    public static EvaluationResult check(ArchRule rule, Class<?>... classesToCheck) {
        Set<Location> locs = Arrays.stream(classesToCheck)
                .map(Locations::ofClass)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        List<URL> uris = Arrays.stream(classesToCheck)
                .map(clazz -> clazz.getPackage().getName())
                .map(Locations::ofPackage)
                .flatMap(it -> it.stream().map(Location::asURI))
                .map(u -> URI.create(u.toASCIIString() + "package-info.class"))
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (MalformedURLException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        locs.addAll(Locations.of(uris));
        final JavaClasses classes = new ClassFileImporter()
                .importLocations(locs);
        return check(rule, classes);
    }
}
