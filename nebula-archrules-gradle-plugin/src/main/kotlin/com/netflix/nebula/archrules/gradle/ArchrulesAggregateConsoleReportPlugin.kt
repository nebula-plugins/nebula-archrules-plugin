package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.VerificationType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register

class ArchrulesAggregateConsoleReportPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext  = project.extensions.create("archRulesAggregate", ArchrulesAggregateExtension::class.java)
        ext.skipPassingSummaries.set(false)
        ext.consoleDetailsThreshold(Priority.MEDIUM)
        val archRulesAggregateDependencies = project.configurations.dependencyScope("archRulesAggregateDependencies") {
            description = "projects to collect archrules data from"
        }
        val archRulesDataFiles = project.configurations.resolvable("archRulesDataFiles") {
            extendsFrom(archRulesAggregateDependencies.get())
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.VERIFICATION))
                attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, project.objects.named("arch-rules"))
            }
        }
        project.subprojects {
            project.dependencies.add("archRulesAggregateDependencies", project.dependencies.project(":$name"))
        }
        project.tasks.register<PrintConsoleReportTask>("archRulesAggregateConsoleReport") {
            dataFiles.from(archRulesDataFiles.map {
                it.incoming.artifactView {
                   lenient(true) // to handle the case where a subproject doesn't have archrules runner
                }.files
            })
            summaryForPassingDisabled.set(ext.skipPassingSummaries)
            detailsThreshold.set(ext.consoleDetailsThreshold)
        }
    }
}
