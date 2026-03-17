package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

/**
 * @deprecated Unused class
 */
@Deprecated(forRemoval = true)
@NullMarked
public record RuleSummary(
        String ruleClass,
        String ruleName,
        Priority priority,
        int failures
) implements Serializable {
}
