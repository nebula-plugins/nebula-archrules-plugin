package com.netflix.nebula.archrules.gradle;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.jspecify.annotations.NonNull;

/**
 * Attribute used to denote an archrules library, and to resolve archrules variants
 */
public interface ArchRuleAttribute extends Named {

    /**
     * Attribute used to denote an archrules library, and to resolve archrules variants
     */
    Attribute<@NonNull ArchRuleAttribute> ARCH_RULES_ATTRIBUTE =
            Attribute.of("com.netflix.nebula.archrules", ArchRuleAttribute.class);

    /**
     * The only recognized value of this attribute
     */
    String ARCH_RULES = "arch-rules";
}
