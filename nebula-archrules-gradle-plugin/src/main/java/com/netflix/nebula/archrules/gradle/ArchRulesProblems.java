package com.netflix.nebula.archrules.gradle;

import org.gradle.api.problems.ProblemGroup;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class ArchRulesProblems {
    public static final ProblemGroup ARCH_RULES =
            ProblemGroup.create("com.netflix.nebula.archrules", "Nebula ArchRules");
}
