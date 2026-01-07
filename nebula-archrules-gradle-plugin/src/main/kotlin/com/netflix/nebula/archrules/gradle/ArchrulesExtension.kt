package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.provider.ListProperty
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
}