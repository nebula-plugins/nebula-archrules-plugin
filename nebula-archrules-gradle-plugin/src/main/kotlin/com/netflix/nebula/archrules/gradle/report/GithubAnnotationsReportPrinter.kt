package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.tngtech.archunit.lang.Priority
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class GithubAnnotationsReportPrinter(
    val writer: OutputStream,
    val sourceFiles: Iterable<File>,
    val projectRoot: File
) : TextReportPrinter {
    override fun printSummaryHeader() {

    }

    override fun printSummaryRuleClass(ruleClass: String) {

    }

    override fun printSummaryRule(rule: Rule, failures: Int) {

    }

    override fun printMoreInfo(detailsThreshold: Priority?) {

    }

    override fun printDetailsHeader() {

    }

    override fun printRuleDetail(rule: Rule) {

    }

    override fun printRuleViolationDetail(result: RuleResult) {
        val level = when (result.rule().priority()) {
            Priority.LOW -> "notice"
            else -> "warning"
        }
        val location = result.message().substringAfterLast("in (").substringBefore(")")
        val fileName = location.split(":")[0]
        val filePath = sourceFiles
            .filter { it.name == fileName }
            .map { it.relativeTo(projectRoot).path }
            .firstOrNull()
            ?: fileName
        val lineNumber = location.split(":")[1].toIntOrNull()
        val line = buildString {
            append("::$level file=$filePath")
            if (lineNumber != null && lineNumber > 0) {
                append(",line=$lineNumber")
            } else {
                append(",line=1")
            }
            append(",title=${result.rule().description()}::${result.message().substringBeforeLast("in (")}")
        }
        writer.write("$line\n".toByteArray(StandardCharsets.UTF_8))
    }

    override fun printDetailsFooter() {

    }
}
