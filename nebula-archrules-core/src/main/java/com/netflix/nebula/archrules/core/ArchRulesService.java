package com.netflix.nebula.archrules.core;

import com.tngtech.archunit.lang.ArchRule;

import java.util.Map;

/**
 * Interface used to make ArchRules automatically discoverable.
 * Implementations should be declared as a {@link java.util.ServiceLoader} service.
 */
public interface ArchRulesService {
    /**
     * An ArchRulesService implementation will produce a map of rules to be discovered for evaluation.
     * The map keys are the IDs of the rules,
     * which will be used as display names in reporting and for additional configuration in the Gradle plugin.
     *
     * @return the rules to evaluate
     */
    Map<String, ArchRule> getRules();
}
