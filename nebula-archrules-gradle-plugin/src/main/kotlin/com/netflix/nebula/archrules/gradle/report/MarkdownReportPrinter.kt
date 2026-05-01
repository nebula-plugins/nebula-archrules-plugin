package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.tngtech.archunit.lang.Priority
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class MarkdownReportPrinter(val writer: OutputStream) : TextReportPrinter {
    override fun printSummaryHeader() {
        writer.write("# ArchRule Summary\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printSummaryRuleClass(ruleClass: String) {
        writer.write("### RuleClass: $ruleClass\n\n".toByteArray(StandardCharsets.UTF_8))
        writer.write("| Rule Name | Priority | Failures |\n".toByteArray(StandardCharsets.UTF_8))
        writer.write("| --- | --- | --- |\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printSummaryRule(rule: Rule, failures: Int) {
        writer.write("| ${rule.ruleName} | ${rule.priority()} | $failures |\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printMoreInfo(detailsThreshold: Priority?) {
    }

    override fun printDetailsHeader() {
        writer.write("\n# ArchRule Violation Details\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printRuleDetail(rule: Rule) {
        writer.write("### Rule: ${rule.ruleName()} Priority: ${rule.priority()}\n".toByteArray(StandardCharsets.UTF_8))
        writer.write(rule.description().toByteArray(StandardCharsets.UTF_8))
        writer.write("\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printRuleViolationDetail(result: RuleResult) {
        writer.write("- ${result.message()}\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printDetailsFooter() {
    }
}
