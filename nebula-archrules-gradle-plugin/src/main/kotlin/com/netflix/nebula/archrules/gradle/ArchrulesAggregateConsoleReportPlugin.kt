package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.VerificationType
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

class ArchrulesAggregateConsoleReportPlugin @Inject constructor(val objects: ObjectFactory) : Plugin<Project> {

    override fun apply(project: Project) {
        val verification: Category = objects.named(Category.VERIFICATION)
        val archRulesVerificationType: VerificationType = objects.named("arch-rules")
        val ext = project.extensions.create("archRulesAggregate", ArchrulesAggregateExtension::class.java)
        ext.skipPassingSummaries.set(false)
        ext.consoleDetailsThreshold(Priority.MEDIUM)
        val archRulesAggregateDependencies = project.configurations.dependencyScope("archRulesAggregateDependencies") {
            description = "projects to collect archrules data from"
        }
        val archRulesDataFiles = project.configurations.resolvable("archRulesDataFiles") {
            extendsFrom(archRulesAggregateDependencies.get())
            attributes {
                attribute(Category.CATEGORY_ATTRIBUTE, verification)
                attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, archRulesVerificationType)
            }
        }

        project.subprojects {
            project.dependencies.add("archRulesAggregateDependencies", project.dependencies.project(path))
        }

        project.tasks.register<PrintConsoleReportTask>("archRulesAggregateConsoleReport") {
            dataFiles.from(archRulesDataFiles.flatMap {
                it.incoming.artifactView {
                    lenient(true) // to handle the case where a subproject doesn't have archrules runner
                }.artifacts.resolvedArtifacts.map {
                    // filter for only artifacts that match the attributes
                    it.filter {
                        it.hasCategory(verification) && it.hasVerificationType(archRulesVerificationType)
                    }.map {
                        it.file
                    }
                }
            })
            summaryForPassingDisabled.set(ext.skipPassingSummaries)
            detailsThreshold.set(ext.consoleDetailsThreshold)
        }
    }

    fun ResolvedArtifactResult.hasCategory(category: Category): Boolean =
        variant.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name == category.name

    fun ResolvedArtifactResult.hasVerificationType(type: VerificationType): Boolean =
        variant.attributes.getAttribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE)?.name == type.name
}
