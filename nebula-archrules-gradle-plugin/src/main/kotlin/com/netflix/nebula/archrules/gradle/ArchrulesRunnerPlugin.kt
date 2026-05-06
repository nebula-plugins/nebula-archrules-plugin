package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import com.tngtech.archunit.lang.Priority
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.VerificationType
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class ArchrulesRunnerPlugin : Plugin<Project> {
    companion object {
        private const val ARCHRULES_VERSION = "0.+" // keep in sync
        private const val JACKSON_VERSION = "3.1.0" // keep in sync with compileOnly dependency
        private const val ARCHRULES_DEPENDENCY = "com.netflix.nebula:nebula-archrules-gradle-plugin:$ARCHRULES_VERSION"
        private const val JACKSON_DEPENDENCY = "tools.jackson.core:jackson-databind:$JACKSON_VERSION"
    }
    override fun apply(project: Project) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        project.configurations.register("archRules") {
            isCanBeConsumed = false
            isCanBeResolved = true
            attributes {
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
            archRulesExt.jsonReportEnabled.convention(true)
            archRulesExt.markdownReportEnabled.convention(true)
            archRulesExt.skipPassingSummaries.convention(false)
            archRulesExt.sourceSetsToSkip.add("archRulesTest")
            archRulesExt.consoleDetailsThreshold.convention(Priority.MEDIUM)
            project.extensions.getByType<JavaPluginExtension>().sourceSets
                .configureEach {
                    project.configureCheckTaskForSourceSet(this, archRulesExt)
                }

            val jsonReportTask = project.tasks.register<PrintJsonReportTask>("archRulesJsonReport") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                getJsonReportFile().set(archRulesReportDir.map { it.file("report.json").asFile })
                reportingClasspath.setFrom(project.configurations.detachedConfiguration(
                    project.dependencies.create(ARCHRULES_DEPENDENCY),
                    project.dependencies.create(JACKSON_DEPENDENCY)
                ))
                onlyIf { archRulesExt.jsonReportEnabled.get() }
            }

            val consoleReportTask = project.tasks.register<PrintConsoleReportTask>("archRulesConsoleReport") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                summaryForPassingDisabled.set(archRulesExt.skipPassingSummaries)
                detailsThreshold.set(archRulesExt.consoleDetailsThreshold)
                onlyIf { archRulesExt.consoleReportEnabled.get() }
            }

            val markdownReportTask = project.tasks.register<PrintMarkdownReportTask>("archRulesMarkdownReport") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                markdownReportFile.set(archRulesReportDir.map { it.file("report.md") })
                detailsThreshold.set(archRulesExt.consoleDetailsThreshold)
                onlyIf { archRulesExt.markdownReportEnabled.get() }
            }

            project.configurations.consumable("archRulesReportElements") {
                description = "Report data for ArchRules"
                outgoing.artifacts(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                ){
                    type = ArtifactTypeDefinition.BINARY_DATA_TYPE
                    builtBy(project.tasks.withType<CheckRulesTask>())
                }
                attributes {
                    attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.VERIFICATION))
                    attribute(VerificationType.VERIFICATION_TYPE_ATTRIBUTE, project.objects.named("arch-rules"))
                }
            }

            val enforceTask = project.tasks.register<EnforceArchRulesTask>("enforceArchRules") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                failureThreshold.set(archRulesExt.failureThreshold)
                onlyIf { failureThreshold.isPresent }
            }

            project.tasks.named("check") {
                dependsOn(enforceTask)
                finalizedBy(jsonReportTask, markdownReportTask, consoleReportTask)
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
            attributes.addAllLater(project.configurations.getByName(sourceSet.runtimeClasspathConfigurationName).attributes)
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(ARCH_RULES))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.CLASSES))
            }
        }

        tasks.register<CheckRulesTask>("checkArchRules" + sourceSet.name.capitalized()) {
            description = "Checks ArchRules on ${sourceSet.name}"
            rulesClasspath.setFrom(sourceSetArchRulesRuntime)
            priorityOverridesByName.set(
                ext.ruleOverrides.map {
                    it.mapValues { it.value.priority }
                        .filterValues { it != null }
                        .mapValues { it.value!! } // could be improved by https://youtrack.jetbrains.com/issue/KT-4734
                }
            )
            priorityOverridesByClass.set(
                ext.ruleClassOverrides.map {
                    it.mapValues { it.value.priority }
                        .filterValues { it != null }
                        .mapValues { it.value!! } // could be improved by https://youtrack.jetbrains.com/issue/KT-4734
                }
            )
            excludedRules.set(
                ext.ruleOverrides.map {
                    it.filter { it.value.sourceSetsToSkip.contains(sourceSet.name) }.map { it.key }
                }
            )
            excludedRuleClasses.set(
                ext.ruleClassOverrides.map {
                    it.filter { it.value.sourceSetsToSkip.contains(sourceSet.name) }.map { it.key }
                }
            )
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
