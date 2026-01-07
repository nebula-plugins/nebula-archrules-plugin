package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File

@CacheableTask
abstract class EnforceArchRulesTask : DefaultTask() {

    /**
     * The data files to read in. These files should container binary data representing [RuleResult]s
     * @return all data files to process
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ListProperty<File>

    /**
     * The data files to read in. These files should container binary data representing [RuleResult]s
     * @return all data files to process
     */
    @get:Input
    @get:Optional
    abstract val failureThreshold: Property<Priority>

    @TaskAction
    fun enforce() {
        val criticalFailures = dataFiles.get()
            .filter { it.exists() }
            .flatMap { ViolationsUtil.readDetails(it) }
            .filter { it.status == RuleResultStatus.FAIL }
            .filter { shouldFail(it.rule.priority) }
        if (criticalFailures.isNotEmpty()) {
            throw RuntimeException(
                "ArchRules failed: ${
                    criticalFailures.joinToString("\n") {
                        "${it.rule.ruleName} (${it.rule.priority})"
                    }
                }"
            )
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