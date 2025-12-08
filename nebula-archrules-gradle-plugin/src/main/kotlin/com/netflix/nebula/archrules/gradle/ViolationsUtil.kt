package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.internal.logging.text.StyledTextOutput
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream

/**
 * Helpers for dealing with [RuleResult]
 */
class ViolationsUtil {
    companion object {
        @JvmStatic
        fun readDetails(dataFile: File): List<RuleResult> {
            val list: MutableList<RuleResult> = mutableListOf()
            try {
                ObjectInputStream(FileInputStream(dataFile)).use { objectInputStream ->
                    val numObjects = objectInputStream.readInt()
                    repeat(numObjects) {
                        list.add(objectInputStream.readObject() as RuleResult)
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
            return list
        }

        @JvmStatic
        fun printReport(violations: Map<Rule, List<RuleResult>>, output: StyledTextOutput, infoLogging: Boolean) {
            violations
                .mapValues { it.value.filter { it.rule().priority() != Priority.LOW || infoLogging } }
                .filter { it.value.isNotEmpty() }
                .forEach { (rule, ruleViolations) ->
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
                    ruleViolations.forEach {
                        output.style(style).println("    " + it.message())
                    }
                    output.println()
                }
        }

        @JvmStatic
        fun printSummary(resultMap: Map<Rule, List<RuleResult>>, output: StyledTextOutput) {
            val indent = 4
            val maxRuleNameLength = resultMap.keys.maxOfOrNull { it.ruleName().length } ?: 1
            resultMap.entries.groupBy { entry -> entry.key.ruleClass() }
                .forEach { (ruleClass, classMap) ->
                    output.style(StyledTextOutput.Style.Header).println(ruleClass)
                    classMap.forEach { (rule, results) ->
                        val failures = results.filter { it.status() != RuleResultStatus.PASS }
                        if (failures.isEmpty()) {
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
                                .println(" (" + failures.size + " failures)")
                        }
                    }
            }
        }

        /**
         * Rules which fail due to no match should only count as a failure if they fail for every source set in which that rule was run
         */
        @JvmStatic
        fun consolidatedFailures(violations: List<RuleResult>): Map<Rule, List<RuleResult>> {
            val byType = violations.groupBy { it.rule() }.mapValues { it.value.toSet() }
            return byType
                .mapValues { (_, fullSet) ->
                    fullSet.filter { !(it.status() == RuleResultStatus.NO_MATCH && fullSet.size != 1) }
                }
                .mapValues { it.value.filter { it.status() != RuleResultStatus.PASS } }
        }
    }
}