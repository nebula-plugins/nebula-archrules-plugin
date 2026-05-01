package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus
import com.netflix.nebula.archrules.gradle.meetsThreshold
import com.tngtech.archunit.lang.Priority

/**
 * abstraction for text-based reports
 */
interface TextReportPrinter {
    fun print(ruleResults: List<RuleResult>, skipPassing: Boolean, infoLogging: Boolean, detailsThreshold: Priority?) {
        val byRule = FailuresByRuleBuilder.build(ruleResults)
        printSummary(byRule, skipPassing, infoLogging)
        if (ruleResults.any {
                it.status() == RuleResultStatus.FAIL &&
                    !it.rule().priority().meetsThreshold(detailsThreshold) &&
                    !infoLogging
            }) {
            printMoreInfo(detailsThreshold)
        }
        printDetails(byRule, infoLogging, detailsThreshold)
    }

    fun printSummary(failuresByRule: FailuresByRule, skipPassing: Boolean, infoLogging: Boolean) {
        printSummaryHeader()
        failuresByRule.entries.groupBy { entry -> entry.key.ruleClass() }
            .forEach { (ruleClass, classMap) ->
                val classHasFailures = classMap.flatMap { it.value }.any { it.status != RuleResultStatus.PASS }
                val shouldClassPrint = !skipPassing || infoLogging || classHasFailures
                if (shouldClassPrint) {
                    printSummaryRuleClass(ruleClass)
                    classMap.forEach { (rule, results) ->
                        val failures = results.filter { it.status() != RuleResultStatus.PASS }
                        if (!failures.isEmpty() || !skipPassing || infoLogging) {
                            printSummaryRule(rule, failures.size)
                        }
                    }
                }
            }
    }

    fun printDetails(failuresByRule: FailuresByRule, infoLogging: Boolean, detailsThreshold: Priority?) {
        printDetailsHeader()
        failuresByRule
            .mapValues { it.value.filter { it.rule().priority().meetsThreshold(detailsThreshold) || infoLogging } }
            .filter { it.value.isNotEmpty() }
            .forEach { (rule, ruleViolations) ->
                printRuleDetail(rule)
                ruleViolations.forEach {
                    printRuleViolationDetail(it)
                }
                printDetailsFooter()
            }
    }

    fun printSummaryHeader()
    fun printSummaryRuleClass(ruleClass: String)
    fun printSummaryRule(rule: Rule, failures: Int)
    fun printMoreInfo(detailsThreshold: Priority?)
    fun printDetailsHeader()
    fun printRuleDetail(rule: Rule)
    fun printRuleViolationDetail(result: RuleResult)
    fun printDetailsFooter()
}
