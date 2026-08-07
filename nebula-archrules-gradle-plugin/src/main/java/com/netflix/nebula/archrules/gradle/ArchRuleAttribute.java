package com.netflix.nebula.archrules.gradle;

import org.gradle.api.attributes.Attribute;
import org.jspecify.annotations.NullMarked;

/**
 * Attribute used to denote an archrules library, and to resolve archrules variants
 */
@NullMarked
public interface ArchRuleAttribute {

    /**
     * Attribute used to denote an archrules library, and to resolve archrules variants
     */
    Attribute<String> ARCH_RULES_ATTRIBUTE =
            Attribute.of("com.netflix.nebula.archrules", String.class);

    /**
     * Used in archrules artifacts to allow them to be selected via the variant selection algorithm
     */
    String ARCH_RULES = "arch-rules";
}
