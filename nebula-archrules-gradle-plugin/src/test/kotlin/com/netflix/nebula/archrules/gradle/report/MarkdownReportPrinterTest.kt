package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus
import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

internal class MarkdownReportPrinterTest {
    @Test
    fun `test print no failures`() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
            val instance = MarkdownReportPrinter(it)
            instance.print(results, false, true, null)
            assertThat(it.toString(StandardCharsets.UTF_8))
                .contains("# ArchRule Summary")
                .contains("### RuleClass: RuleClass")
                .contains("| RuleName | MEDIUM | 0 |")
        }
    }

    @Test
    fun `test print with failures`() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(RuleResult(rule, "message", RuleResultStatus.FAIL))
            val instance = MarkdownReportPrinter(it)
            instance.print(results, false, true, null)
            assertThat(it.toString(StandardCharsets.UTF_8))
                .contains("# ArchRule Summary")
                .contains("### RuleClass: RuleClass")
                .contains("| RuleName | MEDIUM | 1 |")
                .contains("### Rule: RuleName Priority: MEDIUM")
                .contains("description")
                .contains("- message")
        }
    }

    @Test
    fun `test printSummary`() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
            val instance = MarkdownReportPrinter(it)
            instance.printSummary(mapOf(rule to results), false, false)
            assertThat(it.toString(StandardCharsets.UTF_8))
                .contains("# ArchRule Summary")
                .contains("### RuleClass: RuleClass")
                .contains("| RuleName | MEDIUM | 0 |")
        }
    }

    @Test
    fun `test printSummary empty results`() {
        ByteArrayOutputStream().use {
            val instance = MarkdownReportPrinter(it)
            instance.printSummary(mapOf(), false, false)
            assertThat(it.toString(StandardCharsets.UTF_8)).isEqualTo("# ArchRule Summary\n")
        }
    }
}
