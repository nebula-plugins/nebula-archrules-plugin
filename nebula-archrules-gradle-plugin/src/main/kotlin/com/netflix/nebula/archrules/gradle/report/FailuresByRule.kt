package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus

typealias FailuresByRule = Map<Rule, List<RuleResult>>

object FailuresByRuleBuilder {
    /**
     * Same as [ResultsByRule] but filters out passing rules
     */
    @JvmStatic
    fun build(violations: List<RuleResult>): FailuresByRule {
        return ResultsByRuleBuilder.build(violations)
            .mapValues {
                it.value.filter { it.status() != RuleResultStatus.PASS }
            }
    }
}
