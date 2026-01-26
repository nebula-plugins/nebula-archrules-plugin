package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

abstract class ArchrulesExtension {
    /**
     * set to false to disable the console report
     */
    abstract val consoleReportEnabled: Property<Boolean>

    /**
     * Skip printing lines in the console report summary for passing rules
     */
    abstract val skipPassingSummaries: Property<Boolean>
    abstract val sourceSetsToSkip: ListProperty<String>

    abstract val failureThreshold: Property<Priority>
    abstract val consoleDetailsThreshold: Property<Priority>

    /**
     * Allow priority overrides
     */
    abstract val priorityOverrides: MapProperty<String, Priority>

    /**
     * Add a source set to the list of sourcesets to skip
     */
    fun skipSourceSet(name: String) {
        sourceSetsToSkip.add(name)
    }

    fun failureThreshold(priority: Priority) {
        failureThreshold.set(priority)
    }

    fun failureThreshold(priority: String) {
        failureThreshold.set(Priority.valueOf(priority))
    }

    fun consoleDetailsThreshold(priority: Priority) {
        consoleDetailsThreshold.set(priority)
    }

    fun consoleDetailsThreshold(priority: String) {
        consoleDetailsThreshold.set(Priority.valueOf(priority))
    }

    fun rule(ruleClass: String, action: Action<RuleConfig>) {
        val config = RuleConfig()
        action.execute(config)

        config.priority?.let { priority ->
            priorityOverrides.put(ruleClass, priority)
        }
    }
}

class RuleConfig {
    var priority: Priority? = null

    fun priority(priority: String) {
        this.priority = Priority.valueOf(priority)
    }
}