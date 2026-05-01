package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.tngtech.archunit.lang.Priority
import org.gradle.internal.logging.text.StyledTextOutput

class RichConsoleReportPrinter(val output: StyledTextOutput, val maxRuleNameLength: Int) : TextReportPrinter {
    companion object {
        fun calculateMaxRuleNameLength(results: List<RuleResult>): Int {
            return results.maxOfOrNull { it.rule().ruleName().length } ?: 1
        }
    }
    val indent = 4
    override fun printSummaryHeader() {
        output.style(StyledTextOutput.Style.Header).println("ArchRule Summary:")
    }

    override fun printSummaryRuleClass(ruleClass: String) {
        output.style(StyledTextOutput.Style.Header).println(ruleClass)
    }

    override fun printSummaryRule(rule: Rule, failures: Int) {
        if (failures == 0) {
            output.style(StyledTextOutput.Style.Success)
                .text(" ".repeat(indent))
                .text(rule.ruleName().padEnd(maxRuleNameLength + 1))
                .text(" ")
                .text(rule.priority().asString().padEnd(10))
                .println(" (No failures)")
        } else {
            val style = when (rule.priority()) {
                Priority.LOW -> StyledTextOutput.Style.Normal
                Priority.MEDIUM -> StyledTextOutput.Style.Info
                Priority.HIGH -> StyledTextOutput.Style.Failure
            }
            output.style(style)
                .text(" ".repeat(indent))
                .text(rule.ruleName().padEnd(maxRuleNameLength + 1))
                .text(" ")
                .text(rule.priority().asString().padEnd(10))
                .println(" ($failures failures)")
        }
    }

    override fun printMoreInfo(detailsThreshold: Priority?) {
        output.style(StyledTextOutput.Style.Header)
            .text("Note: ")
            .style(StyledTextOutput.Style.Normal)
            .println("In order to see details of rules with priority less than ${detailsThreshold ?: Priority.LOW}, run build with --info")

    }

    override fun printDetailsHeader() {
        output.style(StyledTextOutput.Style.Header).println("ArchRule Violation Details:")
    }

    override fun printRuleDetail(rule: Rule) {
        val style = when (rule.priority()) {
            Priority.LOW -> StyledTextOutput.Style.Normal
            Priority.MEDIUM -> StyledTextOutput.Style.Info
            Priority.HIGH -> StyledTextOutput.Style.Failure
        }
        output
            .style(StyledTextOutput.Style.Header).text("Rule: ${rule.ruleName} Priority: ")
            .style(style)
            .println(rule.priority().asString())
            .style(style)
            .println(rule.description())
    }

    override fun printRuleViolationDetail(result: RuleResult) {
        val style = when (result.rule().priority()) {
            Priority.LOW -> StyledTextOutput.Style.Normal
            Priority.MEDIUM -> StyledTextOutput.Style.Info
            Priority.HIGH -> StyledTextOutput.Style.Failure
        }
        output.style(style).println("    " + result.message())
    }

    override fun printDetailsFooter() {
        output.println()
    }
}
