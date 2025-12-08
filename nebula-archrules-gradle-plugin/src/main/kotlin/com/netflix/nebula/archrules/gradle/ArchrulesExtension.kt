package com.netflix.nebula.archrules.gradle

import org.gradle.api.provider.Property

abstract class ArchrulesExtension {
    abstract val consoleReportEnabled: Property<Boolean>
    abstract val skipPassingSummaries: Property<Boolean>
}