package com.netflix.nebula.archrules.gradle;

import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

@NullMarked
public record RuleResult(
        Rule rule,
        String message,
        RuleResultStatus status
) implements Serializable {
}
