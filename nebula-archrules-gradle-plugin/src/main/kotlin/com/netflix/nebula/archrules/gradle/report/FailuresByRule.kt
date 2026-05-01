package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus

typealias FailuresByRule = Map<Rule, List<RuleResult>>

object FailuresByRuleBuilder {
    /**
     * Rules which fail due to no match should only count as a failure if they fail for every source set in which that rule was run
     */
    @JvmStatic
    fun build(violations: List<RuleResult>): FailuresByRule {
        val byType = violations.groupBy { it.rule() }.mapValues { it.value.toSet() }
        return byType
            .mapValues { (_, fullSet) ->
                fullSet.filter { !(it.status() == RuleResultStatus.NO_MATCH && fullSet.size != 1) }
            }
            .mapValues { it.value.filter { it.status() != RuleResultStatus.PASS } }
    }
}
