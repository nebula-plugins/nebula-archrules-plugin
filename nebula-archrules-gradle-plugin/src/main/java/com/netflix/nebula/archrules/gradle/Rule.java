package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;

import java.io.Serializable;

public record Rule(String ruleClass, String ruleName, String description, Priority priority) implements Serializable {
}
