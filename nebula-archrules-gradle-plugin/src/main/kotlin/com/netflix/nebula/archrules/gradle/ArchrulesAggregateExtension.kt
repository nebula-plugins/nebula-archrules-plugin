package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Settings for the aggregate console report
 */
abstract class ArchrulesAggregateExtension {

    /**
     * Skip printing lines in the console report summary for passing rules
     */
    abstract val skipPassingSummaries: Property<Boolean>
    abstract val consoleDetailsThreshold: Property<Priority>

    fun consoleDetailsThreshold(priority: Priority) {
        consoleDetailsThreshold.set(priority)
    }

    fun consoleDetailsThreshold(priority: String) {
        consoleDetailsThreshold.set(Priority.valueOf(priority))
    }
}
