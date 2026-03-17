package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;
import org.jspecify.annotations.NullMarked;

import java.io.Serializable;

@NullMarked
public record Rule(String ruleClass, String ruleName, String description, Priority priority) implements Serializable {
}
