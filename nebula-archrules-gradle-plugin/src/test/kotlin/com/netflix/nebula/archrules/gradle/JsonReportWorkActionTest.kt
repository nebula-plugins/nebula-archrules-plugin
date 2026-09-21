package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import net.javacrumbs.jsonunit.assertj.assertThatJson
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal class JsonReportWorkActionTest {

    @Test
    fun test() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(
                RuleResult(
                    rule,
                    "message",
                    RuleResultStatus.FAIL
                )
            )
            JsonReportWorkAction.print(results, it)
            val actualJson = it.toString(StandardCharsets.UTF_8)
            assertThatJson(actualJson)
                .inPath("$.violations")
                .isArray
                .hasSize(1)
        }
    }

    @Test
    fun test_no_match_fail() {
        ByteArrayOutputStream().use {
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
            JsonReportWorkAction.print(results, it)
            val actualJson = it.toString(StandardCharsets.UTF_8)
            assertThatJson(actualJson)
                .inPath("$.violations")
                .isArray
                .hasSize(1)
        }
    }

    @Test
    fun test_no_match_pass() {
        ByteArrayOutputStream().use {
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
            JsonReportWorkAction.print(results, it)
            val actualJson = it.toString(StandardCharsets.UTF_8)
            assertThatJson(actualJson)
                .inPath("$.violations")
                .isArray
                .hasSize(1)
            assertThatJson(actualJson)
                .inPath("$.violations[0].status")
                .isEqualTo("PASS")
        }
    }

    @Test
    fun test_pass() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(
                RuleResult(
                    rule,
                    "message",
                    RuleResultStatus.PASS
                )
            )
            JsonReportWorkAction.print(results, it)
            val actualJson = it.toString(StandardCharsets.UTF_8)
            assertThatJson(actualJson)
                .`as`("passing results are included in json report")
                .inPath("$.violations")
                .isArray
                .hasSize(1)
        }
    }
}
