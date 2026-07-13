package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import javax.inject.Inject

/**
 * This ensures that the archrules are preferred over runtime libs in archrule configurations
 */
abstract class ArchRuleUsageDisambiguationRule @Inject constructor(
    val javaApi: Usage,
    val javaRuntime: Usage,
    val archRules: Usage
) :
    AttributeDisambiguationRule<Usage> {
    override fun execute(d: MultipleCandidatesDetails<Usage>) {
        val candidates = d.candidateValues.map { it.name }
        if (d.consumerValue?.name == ARCH_RULES) {
            if (candidates.contains(ARCH_RULES)) {
                d.closestMatch(archRules)
            } else if (candidates.contains(Usage.JAVA_API)) {
                d.closestMatch(javaApi)
            } else if (candidates.contains(Usage.JAVA_RUNTIME)) {
                d.closestMatch(javaRuntime)
            }
        } else if (d.consumerValue?.name == Usage.JAVA_RUNTIME) {
            if (candidates.contains(Usage.JAVA_RUNTIME)) {
                d.closestMatch(javaRuntime)
            }
        } else if (d.consumerValue?.name == Usage.JAVA_API) {
            if (candidates.contains(Usage.JAVA_API)) {
                d.closestMatch(javaApi)
            }
        }
    }
}
