package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.RuleResult
import com.tngtech.archunit.lang.Priority
import java.io.File

fun fromRuleResult(projectRoot: File, sourceFiles: Iterable<File>, result: RuleResult): GithubAnnotation {
    val level = when (result.rule.priority) {
        Priority.LOW -> "notice"
        Priority.MEDIUM -> "warning"
        Priority.HIGH -> "error"
    }
    val location = result.message().substringAfterLast("in (").substringBefore(")")
    val fileName = location.split(":")[0]
    val filePath = sourceFiles
        .filter { it.name == fileName }
        .map { it.relativeTo(projectRoot).path }
        .firstOrNull() ?: fileName
    val lineNumber: Int = location.split(":")[1].toIntOrNull()!!
    return GithubAnnotation(
        filePath,
        level,
        result.rule.ruleName,
        result.rule.description,
        result.message,
        lineNumber
    )
}
