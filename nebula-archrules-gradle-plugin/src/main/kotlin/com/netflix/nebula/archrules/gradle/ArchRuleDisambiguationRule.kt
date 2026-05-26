package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named
import javax.inject.Inject

/**
 * This ensures that the archrules are preferred over runtime libs in archrule configurations
 */
abstract class ArchRuleDisambiguationRule @Inject constructor(val objects: ObjectFactory) :
    AttributeDisambiguationRule<Usage> {

    override fun execute(d: MultipleCandidatesDetails<Usage>) {
        val candidates = d.candidateValues.map { it.name }
        if (d.consumerValue?.name == ARCH_RULES) {
            if (candidates.contains(ARCH_RULES)) {
                d.closestMatch(objects.named<Usage>(ARCH_RULES))
            }
        } else if (d.consumerValue?.name == Usage.JAVA_RUNTIME) {
            if (candidates.contains(Usage.JAVA_RUNTIME)) {
                d.closestMatch(objects.named<Usage>(Usage.JAVA_RUNTIME))
            }
        }
    }
}
