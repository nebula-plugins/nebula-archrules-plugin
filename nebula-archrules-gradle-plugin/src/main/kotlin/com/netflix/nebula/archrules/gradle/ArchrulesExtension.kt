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
     * set to false to disable the json report
     */
    abstract val jsonReportEnabled: Property<Boolean>

    /**
     * Skip printing lines in the console report summary for passing rules
     */
    abstract val skipPassingSummaries: Property<Boolean>
    abstract val sourceSetsToSkip: ListProperty<String>

    abstract val failureThreshold: Property<Priority>
    abstract val consoleDetailsThreshold: Property<Priority>

    abstract val ruleOverrides: MapProperty<String, RuleConfig>
    abstract val ruleClassOverrides: MapProperty<String, RuleConfig>

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

    @Deprecated("use ruleName instead")
    fun rule(ruleName: String, action: Action<RuleConfig>) {
        ruleOverrides.put(ruleName, RuleConfig().apply { action.execute(this) })
    }

    fun ruleName(ruleName: String, action: Action<RuleConfig>) {
        ruleOverrides.put(ruleName, RuleConfig().apply { action.execute(this) })
    }

    fun ruleClass(ruleClass: String, action: Action<RuleConfig>) {
        ruleClassOverrides.put(ruleClass, RuleConfig().apply { action.execute(this) })
    }
}
