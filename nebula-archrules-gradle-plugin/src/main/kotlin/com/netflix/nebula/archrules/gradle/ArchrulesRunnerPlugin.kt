package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class ArchrulesRunnerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        project.plugins.withId("java") {
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
            val archRulesExt = project.extensions.create<ArchrulesExtension>("archRules")
            archRulesExt.consoleReportEnabled.convention(true)
            val checkTasks = project.extensions.getByType<JavaPluginExtension>().sourceSets
                .map { sourceSet ->
                    val checkTask =
                        project.tasks.register<CheckRulesTask>("checkArchRules" + sourceSet.name.capitalized()) {
                            description = "Checks ArchRules on ${sourceSet.name}"
                            val artifactView =
                                project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName)
                                    .incoming
                                    .artifactView {
                                        withVariantReselection()
                                        attributes {
                                            attribute(
                                                ArchRuleAttribute.ARCH_RULES_ATTRIBUTE,
                                                project.objects.named<ArchRuleAttribute>(ARCH_RULES)
                                            )
                                            attribute(
                                                Usage.USAGE_ATTRIBUTE,
                                                project.objects.named<Usage>(Usage.JAVA_RUNTIME)
                                            )
                                            attribute(
                                                Category.CATEGORY_ATTRIBUTE,
                                                project.objects.named<Category>(Category.LIBRARY)
                                            )
                                            attribute(
                                                Bundling.BUNDLING_ATTRIBUTE,
                                                project.objects.named(Bundling.EXTERNAL)
                                            )
                                        }
                                        lenient(false)
                                    }
                            rulesClasspath.setFrom(
                                artifactView.files,
                                project.configurations.getByName("archRules")
                            )
                            dataFile.set(archRulesReportDir.map {
                                it.file(sourceSet.name + ".data").asFile
                            })
                            sourcesToCheck.from(sourceSet.output.classesDirs)
                            dependsOn(project.tasks.named(sourceSet.classesTaskName))
                        }
                    checkTask
                }

            val dataFiles = checkTasks.stream()
                .map { it.get().dataFile.get() }
                .toList()

            val jsonReportTask = project.tasks.register<PrintJsonReportTask>("archRulesJsonReport") {
                getDataFiles().set(dataFiles)
                getJsonReportFile().set(archRulesReportDir.map { it.file("report.json").asFile })
                dependsOn(checkTasks)
            }

            val consoleReportTask = project.tasks.register<PrintConsoleReportTask>("archRulesConsoleReport") {
                getDataFiles().set(dataFiles)
                dependsOn(checkTasks)
                onlyIf { archRulesExt.consoleReportEnabled.get() }
            }

            project.tasks.named("check") {
                dependsOn(checkTasks)
                finalizedBy(jsonReportTask, consoleReportTask)
            }
        }
    }
}