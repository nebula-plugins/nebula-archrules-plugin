package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import com.tngtech.archunit.lang.Priority
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.*

class ArchrulesRunnerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        project.configurations.register("archRules") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
                attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(ARCH_RULES))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named<Category>(Category.LIBRARY))
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
            }
        }
        project.plugins.withId("java") {
            project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE) {
                compatibilityRules.add(ArchRuleCompatibilityRule::class)
            }

            val archRulesExt = project.extensions.create<ArchrulesExtension>("archRules")
            archRulesExt.consoleReportEnabled.convention(true)
            archRulesExt.skipPassingSummaries.convention(false)
            archRulesExt.sourceSetsToSkip.add("archRulesTest")
            archRulesExt.consoleDetailsThreshold.convention(Priority.MEDIUM)
            project.extensions.getByType<JavaPluginExtension>().sourceSets
                .configureEach {
                    project.configureCheckTaskForSourceSet(this, archRulesExt)
                }
            val checkTasks = project.tasks.withType<CheckRulesTask>()
            val jsonReportTask = project.tasks.register<PrintJsonReportTask>("archRulesJsonReport") {
                getDataFiles().set(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                )
                getJsonReportFile().set(archRulesReportDir.map { it.file("report.json").asFile })
                dependsOn(checkTasks)
            }

            val consoleReportTask = project.tasks.register<PrintConsoleReportTask>("archRulesConsoleReport") {
                dataFiles.set(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                )
                summaryForPassingDisabled.set(archRulesExt.skipPassingSummaries)
                detailsThreshold.set(archRulesExt.consoleDetailsThreshold)
                dependsOn(checkTasks)
                onlyIf { archRulesExt.consoleReportEnabled.get() }
            }

            val enforceTask = project.tasks.register<EnforceArchRulesTask>("enforceArchRules") {
                dependsOn(checkTasks)
                dataFiles.set(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                )
                failureThreshold.set(archRulesExt.failureThreshold)
                onlyIf { failureThreshold.isPresent }
            }

            project.tasks.named("check") {
                dependsOn(checkTasks)
                dependsOn(enforceTask)
                finalizedBy(jsonReportTask, consoleReportTask)
            }
        }
    }

    fun Project.configureCheckTaskForSourceSet(sourceSet: SourceSet, ext: ArchrulesExtension) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        val sourceSetArchRulesRuntime = configurations.resolvable(sourceSet.name + "ArchRulesRuntime") {
            extendsFrom(
                project.configurations.getByName("archRules"),
                configurations.getByName(sourceSet.runtimeClasspathConfigurationName)
            )
            attributes {
                attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(ARCH_RULES))
            }
        }
        tasks.register<CheckRulesTask>("checkArchRules" + sourceSet.name.capitalized()) {
            description = "Checks ArchRules on ${sourceSet.name}"
            rulesClasspath.setFrom(sourceSetArchRulesRuntime)
            priorityOverrides.set(ext.priorityOverrides)
            dataFile.set(archRulesReportDir.map {
                it.file(sourceSet.name + ".data").asFile
            })
            sourcesToCheck.from(sourceSet.output.classesDirs)
            dependsOn(project.tasks.named(sourceSet.classesTaskName))
            val sourceSetName = sourceSet.name
            onlyIf { !ext.sourceSetsToSkip.get().contains(sourceSetName) }
        }
    }
}