package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;

import java.io.Serializable;

public record RuleSummary(
        String ruleClass,
        String ruleName,
        Priority priority,
        int failures
) implements Serializable {
}
