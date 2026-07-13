package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage

/**
 * This allows resolution to occur in archrules classpaths where a library does not provide archrules
 */
class ArchRuleUsageCompatibilityRule : AttributeCompatibilityRule<Usage> {
    private val compatibleUsages = setOf(Usage.JAVA_RUNTIME, ARCH_RULES, Usage.JAVA_API)
    override fun execute(t: CompatibilityCheckDetails<Usage>) {
        if (t.consumerValue?.name == ARCH_RULES && compatibleUsages.contains(t.producerValue?.name)) {
            t.compatible()
        } else {
            t.incompatible()
        }
    }
}
