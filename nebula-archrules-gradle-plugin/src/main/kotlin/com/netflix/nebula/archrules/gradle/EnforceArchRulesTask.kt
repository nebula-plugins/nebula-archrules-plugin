package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.report.FailuresByRuleBuilder
import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationException
import javax.inject.Inject

@CacheableTask
abstract class EnforceArchRulesTask : DefaultTask() {

    /**
     * The data files to read in. These files should container binary data representing [RuleResult]s
     * @return all data files to process
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    @get:Inject
    abstract val problems: Problems

    /**
     * The data files to read in. These files should container binary data representing [RuleResult]s
     * @return all data files to process
     */
    @get:Input
    @get:Optional
    abstract val failureThreshold: Property<Priority>

    @get:Input
    @get:Optional
    abstract val warningThreshold: Property<Priority>

    @TaskAction
    fun enforce() {
        val results = dataFiles.files
            .filter { it.exists() }
            .flatMap { ViolationsUtil.readDetails(it) }
            .filter { it.status == RuleResultStatus.FAIL }
        FailuresByRuleBuilder.build(results).forEach { (rule, results) ->
            val id = ProblemId.create(rule.ruleName, rule.description, ArchRulesProblems.ARCH_RULES)
            results.forEach { result ->
                val problem = problems.reporter.create(id) {
                    details(result.message)
                    severity(priorityToSeverity(rule.priority))
                    solution(result.rule.description)
                }
                problems.reporter.report(problem)
            }
        }
        val criticalFailures = results
            .filter { shouldFail(it.rule.priority) }
        if (criticalFailures.isNotEmpty()) {
            val id = ProblemId.create("ArchRules", "ArchRules Critical Failure", ArchRulesProblems.ARCH_RULES)
            val problem = problems.reporter.create(id) {
                severity(Severity.ERROR)
                solution("Fix critical errors reported in Problems Report")
            }
            problems.reporter.throwing(
                VerificationException(
                    "ArchRules failed:\n${
                        criticalFailures.joinToString("\n") {
                            "${it.rule.ruleName} (${it.rule.priority} ${it.message})"
                        }
                    }"
                ), problem)
        }
    }

    fun priorityToSeverity(priority: Priority): Severity {
        return if (shouldFail(priority)) {
            Severity.ERROR
        } else if (shouldWarn(priority)) {
            Severity.WARNING
        } else {
            Severity.ADVICE
        }
    }

    fun shouldWarn(failurePriority: Priority): Boolean {
        return when (warningThreshold.orNull) {
            Priority.HIGH -> failurePriority == Priority.HIGH
            Priority.MEDIUM -> failurePriority == Priority.MEDIUM || failurePriority == Priority.HIGH
            Priority.LOW -> true
            null -> false
        }
    }

    fun shouldFail(failurePriority: Priority): Boolean {
        return when (failureThreshold.orNull) {
            Priority.HIGH -> failurePriority == Priority.HIGH
            Priority.MEDIUM -> failurePriority == Priority.MEDIUM || failurePriority == Priority.HIGH
            Priority.LOW -> true
            null -> false
        }
    }
}
