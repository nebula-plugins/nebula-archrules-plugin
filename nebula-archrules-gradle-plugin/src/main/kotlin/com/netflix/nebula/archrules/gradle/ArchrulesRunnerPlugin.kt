package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
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
                attribute(
                    ArchRuleAttribute.ARCH_RULES_ATTRIBUTE,
                    project.objects.named<ArchRuleAttribute>(ARCH_RULES)
                )
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named<Usage>(Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named<Category>(Category.LIBRARY))
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
            }
        }
        project.plugins.withId("java") {
            val archRulesExt = project.extensions.create<ArchrulesExtension>("archRules")
            archRulesExt.consoleReportEnabled.convention(true)
            archRulesExt.skipPassingSummaries.convention(false)
            project.extensions.getByType<JavaPluginExtension>().sourceSets
                .configureEach {
                    project.configureCheckTaskForSourceSet(this)
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
                getDataFiles().set(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                )
                summaryForPassingDisabled.set(archRulesExt.skipPassingSummaries)
                dependsOn(checkTasks)
                onlyIf { archRulesExt.consoleReportEnabled.get() }
            }

            project.tasks.named("check") {
                dependsOn(checkTasks)
                finalizedBy(jsonReportTask, consoleReportTask)
            }
        }
    }

    fun Project.configureCheckTaskForSourceSet(sourceSet: SourceSet) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        val sourceSetArchRulesRuntime = configurations.resolvable(sourceSet.name + "ArchRulesRuntime") {
            extendsFrom(configurations.getByName(sourceSet.runtimeClasspathConfigurationName))
            attributes {
                attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named<ArchRuleAttribute>(ARCH_RULES))
            }
        }
        tasks.register<CheckRulesTask>("checkArchRules" + sourceSet.name.capitalized()) {
            description = "Checks ArchRules on ${sourceSet.name}"
            rulesClasspath.setFrom(
                sourceSetArchRulesRuntime,
                project.configurations.getByName("archRules")
            )
            dataFile.set(archRulesReportDir.map {
                it.file(sourceSet.name + ".data").asFile
            })
            sourcesToCheck.from(sourceSet.output.classesDirs)
            dependsOn(project.tasks.named(sourceSet.classesTaskName))
        }
    }
}