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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.internal.extensions.stdlib.capitalized
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import javax.inject.Inject

class ArchrulesRunnerPlugin @Inject constructor(val objects: ObjectFactory) : Plugin<Project> {
    companion object {
        private const val ARCHRULES_VERSION = "1.+" // keep in sync
        private const val JACKSON_VERSION = "3.1.0" // keep in sync with compileOnly dependency
        private const val ARCHRULES_DEPENDENCY = "com.netflix.nebula:nebula-archrules-gradle-plugin:$ARCHRULES_VERSION"
        private const val JACKSON_DEPENDENCY = "tools.jackson.core:jackson-databind:$JACKSON_VERSION"
    }

    override fun apply(project: Project) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        val archRulesUsageAttr = objects.named(Usage::class.java, ARCH_RULES)
        project.configurations.dependencyScope("archRules")
        project.plugins.withId("java") {
            project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE) {
                compatibilityRules.add(ArchRuleUsageCompatibilityRule::class)
                disambiguationRules.add(ArchRuleUsageDisambiguationRule::class) {
                    params(project.objects.named(Usage::class.java, Usage.JAVA_API))
                    params(project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                    params(archRulesUsageAttr)
                }
            }

            val archRulesExt = project.extensions.create<ArchrulesExtension>("archRules")
            archRulesExt.consoleReportEnabled.convention(true)
            archRulesExt.jsonReportEnabled.convention(true)
            archRulesExt.markdownReportEnabled.convention(true)
            archRulesExt.skipPassingSummaries.convention(false)
            archRulesExt.githubReportEnabled.convention(
                project.providers.gradleProperty("archrules.github.enabled")
                    .map { it == "true" }
                    .orElse(false)
            )
            archRulesExt.sourceSetsToSkip.add("archRulesTest")
            archRulesExt.consoleDetailsThreshold.convention(Priority.MEDIUM)
            project.extensions.getByType<JavaPluginExtension>().sourceSets
                .configureEach {
                    project.configureCheckTaskForSourceSet(this, archRulesExt)
                }
            val archrulesJsonReportingClasspath = project.configurations.detachedConfiguration(
                project.dependencies.create(ARCHRULES_DEPENDENCY),
                project.dependencies.create(JACKSON_DEPENDENCY)
            ).apply {
                description = "Created by ArchrulesRunnerPlugin to use Jackson in reporting"
            }
            val jsonReportTask = project.tasks.register<PrintJsonReportTask>("archRulesJsonReport") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                getJsonReportFile().set(archRulesReportDir.map { it.file("report.json") })
                reportingClasspath.setFrom(archrulesJsonReportingClasspath)
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

            val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
            val githubRequested = project.gradle.startParameter.taskRequests.any {
                (it.projectPath == project.path || it.projectPath == null) && it.args.contains("archRulesGithubReport")
            }
            val githubReportTask = project.tasks.register<GithubReportTask>("archRulesGithubReport") {
                dataFiles.from(project.tasks.withType<CheckRulesTask>())
                githubReportFile.set(archRulesReportDir.map { it.file("github-annotations.json") })
                sourceFiles.from(javaExt.sourceSets.flatMap { it.allSource })
                projectRoot.set(project.rootProject.layout.projectDirectory)
                detailsThreshold.set(archRulesExt.consoleDetailsThreshold)
                reportingClasspath.setFrom(archrulesJsonReportingClasspath)
                onlyIf { archRulesExt.githubReportEnabled.get() || githubRequested }
            }

            project.configurations.consumable("archRulesReportElements") {
                description = "Report data for ArchRules"
                outgoing.artifacts(
                    project.provider { (project.tasks.withType<CheckRulesTask>().flatMap { it.outputs.files }) }
                ) {
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
                warningThreshold.set(archRulesExt.consoleDetailsThreshold)
            }

            project.tasks.named("check") {
                dependsOn(enforceTask)
                finalizedBy(jsonReportTask, markdownReportTask, consoleReportTask, githubReportTask)
            }
        }

        // workaround for https://github.com/google/protobuf-gradle-plugin/issues/794
        project.pluginManager.withPlugin("com.google.protobuf") {
            project.configurations.configureEach {
                if (name.endsWith("compileProtoPath") || name.endsWith("CompileProtoPath")) {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named<Category>(Category.LIBRARY))
                }
            }
        }
    }

    fun Project.configureCheckTaskForSourceSet(sourceSet: SourceSet, ext: ArchrulesExtension) {
        val archRulesReportDir = project.layout.buildDirectory.dir("reports/archrules")
        val sourceSetArchRulesRuntime = configurations.resolvable(sourceSet.name + "ArchRulesRuntime") {
            extendsFrom(
                project.configurations.getByName("archRules"),
                configurations.getByName(sourceSet.compileClasspathConfigurationName)
            )
            attributes.addAllLater(project.configurations.getByName(sourceSet.compileClasspathConfigurationName).attributes)
            attributes {
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.CLASSES_AND_RESOURCES))
                attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(ARCH_RULES))
            }

            shouldResolveConsistentlyWith(configurations.getByName(sourceSet.compileClasspathConfigurationName))
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
                it.file(sourceSet.name + ".data")
            })
            sourcesToCheck.from(sourceSet.output.classesDirs)
            dependsOn(project.tasks.named(sourceSet.classesTaskName))
            val sourceSetName = sourceSet.name
            skip.set(ext.sourceSetsToSkip.map { it.contains(sourceSetName) })
        }
    }
}
