package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class ArchrulesRunnerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        project.plugins.withId("java") {
            project.configurations.register("archRules") {
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
            project.tasks.named("check") {
                dependsOn(checkTasks)
            }
        }
    }
}