package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails

/**
 * This allows resolution to occur in archrules classpaths where a library does not provide archrules
 */
class ArchRuleCompatibilityRule : AttributeCompatibilityRule<String> {
    override fun execute(t: CompatibilityCheckDetails<String>) {
        if (t.consumerValue == ARCH_RULES && (t.producerValue == null || t.producerValue == t.consumerValue)) {
            t.compatible()
        } else {
            t.incompatible()
        }
    }
}
