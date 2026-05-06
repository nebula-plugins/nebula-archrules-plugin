package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.Usage

/**
 * This allows resolution to occur in archrules classpaths where a library does not provide archrules
 */
class ArchRuleCompatibilityRule : AttributeCompatibilityRule<Usage> {
    override fun execute(t: CompatibilityCheckDetails<Usage>) {
        if (t.consumerValue?.name == ARCH_RULES && t.producerValue?.name == Usage.JAVA_RUNTIME) {
            t.compatible()
        } else if (t.consumerValue?.name == ARCH_RULES && t.producerValue?.name == ARCH_RULES) {
            t.compatible()
        } else {
            t.incompatible()
        }
    }
}
