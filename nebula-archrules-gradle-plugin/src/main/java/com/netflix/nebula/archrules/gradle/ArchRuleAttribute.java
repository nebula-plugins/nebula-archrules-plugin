package com.netflix.nebula.archrules.gradle;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.jspecify.annotations.NonNull;

public interface ArchRuleAttribute extends Named {
    Attribute<@NonNull ArchRuleAttribute> ARCH_RULES_ATTRIBUTE =
            Attribute.of("com.netflix.nebula.archrules", ArchRuleAttribute.class);
    String ARCH_RULES = "arch-rules";
}
