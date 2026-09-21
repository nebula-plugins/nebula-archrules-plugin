package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus
import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResultsByRuleTest {
    @Test
    fun `NO_MATCH all results in NO_MATCH`() {
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(
            RuleResult(
                rule,
                "message",
                RuleResultStatus.NO_MATCH
            ),
            RuleResult(
                rule,
                "message",
                RuleResultStatus.NO_MATCH
            )
        )
        val actual = ResultsByRuleBuilder.build(results)
        assertThat(actual).hasSize(1)
        assertThat(actual[rule]).hasSize(1)
        assertThat(actual[rule]!![0].status).isEqualTo(RuleResultStatus.NO_MATCH)
    }

    @Test
    fun `NO_MATCH mixed results in PASS`() {
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(
            RuleResult(
                rule,
                "message",
                RuleResultStatus.NO_MATCH
            ),
            RuleResult(
                rule,
                "message",
                RuleResultStatus.PASS
            )
        )
        val actual = ResultsByRuleBuilder.build(results)
        assertThat(actual).hasSize(1)
        assertThat(actual[rule]).hasSize(1)
        assertThat(actual[rule]!![0].status).isEqualTo(RuleResultStatus.PASS)
    }
}
