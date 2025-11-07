package com.netflix.nebula.archrules.gradle;

import java.io.Serializable;

public record RuleResult(
        Rule rule,
        String message,
        RuleResultStatus status
) implements Serializable {
}
