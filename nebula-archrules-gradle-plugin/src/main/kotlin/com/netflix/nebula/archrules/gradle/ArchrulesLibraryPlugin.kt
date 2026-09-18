package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.testing.base.TestingExtension
import javax.inject.Inject

class ArchrulesLibraryPlugin @Inject constructor(
    val objects: ObjectFactory,
    val jvmPluginServices: JvmPluginServices
) : Plugin<Project> {

    override fun apply(project: Project) {
        val version = determineVersion()
        val archRulesUsageAttr = objects.named(Usage::class.java, ARCH_RULES)
        project.pluginManager.withPlugin("java-library") {
            project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE) {
                it.compatibilityRules.add(ArchRuleUsageCompatibilityRule::class.java)
                it.disambiguationRules.add(ArchRuleUsageDisambiguationRule::class.java) {
                    it.params(objects.named(Usage::class.java, Usage.JAVA_API))
                    it.params(objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                    it.params(archRulesUsageAttr)
                }
            }
            project.dependencies.attributesSchema.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE) {
                it.compatibilityRules.add(ArchRuleCompatibilityRule::class.java)
            }
            val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
            val mainSourceSet = javaExt.sourceSets.getByName("main")
            val archRulesSourceSet = javaExt.sourceSets.create("archRules")
            val jarTask = project.tasks.register("archRulesJar", Jar::class.java) {
                it.apply {
                    description = "Assembles a jar archive containing the classes of the arch rules."
                    group = "build"
                    manifest.from(
                        project.tasks.named(mainSourceSet.jarTaskName, Jar::class.java)
                            .map(Jar::getManifest).get()
                    )
                    from(archRulesSourceSet.output)
                    archiveClassifier.set("arch-rules")
                }
            }
            project.tasks.named("assemble") { it.dependsOn(jarTask) }
            project.configurations.named(archRulesSourceSet.implementationConfigurationName).configure {
                it.extendsFrom(project.configurations.getByName(mainSourceSet.apiConfigurationName))
            }
            project.configurations.named(archRulesSourceSet.runtimeClasspathConfigurationName).configure {
                it.attributes {
                    it.addAllLater(
                        project.configurations.named(mainSourceSet.runtimeClasspathConfigurationName).get().attributes
                    )
                    it.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                    it.attribute(Usage.USAGE_ATTRIBUTE, archRulesUsageAttr)
                }
            }
            project.configurations.named(archRulesSourceSet.compileClasspathConfigurationName).configure {
                it.attributes {
                    it.addAllLater(
                        project.configurations.named(mainSourceSet.compileClasspathConfigurationName).get().attributes
                    )
                    it.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                    it.attribute(Usage.USAGE_ATTRIBUTE, archRulesUsageAttr)
                }
            }
            project.dependencies.add(
                archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
            registerFeatureForSourceSet(project, archRulesSourceSet, mainSourceSet)
            val generateServicesTask =
                project.tasks.register("generateServicesRegistry", GenerateServicesRegistryTask::class.java) {
                    it.apply {
                        archRuleServicesFile.set(
                            project.layout.buildDirectory.file(
                                "resources/archRules/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"
                            )
                        )
                        ruleSourceClasses.from(
                            project.tasks.named(archRulesSourceSet.compileJavaTaskName, JavaCompile::class.java)
                        )
                        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                            ruleSourceClasses.from(project.tasks.named(archRulesSourceSet.getCompileTaskName("kotlin")))
                        }
                        dependsOn(project.tasks.named(archRulesSourceSet.processResourcesTaskName))
                    }
                }
            jarTask.configure {
                it.dependsOn(generateServicesTask)
            }
            project.tasks.named(archRulesSourceSet.classesTaskName) {
                it.dependsOn(generateServicesTask)
            }
            project.tasks.register("generateRulesDocumentation", GenerateRulesDocumentationTask::class.java) {
                it.apply {
                    description = "Generates documentation for ArchRules"
                    group = "documentation"
                    rulesClasspath.from(archRulesSourceSet.output)
                    rulesClasspath.from(project.configurations.named(archRulesSourceSet.runtimeClasspathConfigurationName))
                    outputFile.convention(
                        project.layout.buildDirectory.file("docs/archrules.md")
                    )
                    libraryName.convention(project.name)
                    dependsOn(generateServicesTask)
                }
            }
            project.pluginManager.withPlugin("jvm-test-suite") {
                val ext = project.extensions.getByType(TestingExtension::class.java)
                ext.suites.register("archRulesTest", JvmTestSuite::class.java) {
                    it.useJUnitJupiter()
                    it.dependencies {
                        it.implementation.add(it.project())
                        it.implementation.add(archRulesSourceSet.output)
                        it.implementation.add("com.netflix.nebula:nebula-archrules-core:$version")
                    }
                    javaExt.sourceSets.named("archRulesTest").configure { archRulesTestSourceSet ->
                        project.tasks.named(archRulesTestSourceSet.compileJavaTaskName) {
                            it.dependsOn(generateServicesTask)
                        }
                        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                            project.tasks.named(archRulesTestSourceSet.getCompileTaskName("kotlin")) {
                                it.dependsOn(generateServicesTask)
                            }
                        }
                        project.configurations.named(archRulesTestSourceSet.implementationConfigurationName) {
                            it.extendsFrom(
                                project.configurations.getByName(javaExt.sourceSets.getByName("main").implementationConfigurationName)
                            )
                        }
                        project.configurations.named(archRulesTestSourceSet.runtimeClasspathConfigurationName)
                            .configure {
                                it.extendsFrom(
                                    project.configurations.getByName(archRulesSourceSet.runtimeClasspathConfigurationName)
                                )
                                it.attributes {
                                    it.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                                    // don't override Usage in order to not mess with junit platform engine dependencies
                                }
                            }
                        project.configurations.named(archRulesTestSourceSet.compileClasspathConfigurationName)
                            .configure {
                                it.extendsFrom(project.configurations.getByName(archRulesSourceSet.compileClasspathConfigurationName))
                                it.attributes {
                                    it.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                                    // don't override Usage in order to not mess with junit platform engine dependencies
                                }
                            }
                    }
                }
                project.tasks.named("check") {
                    it.dependsOn(ext.suites.named("archRulesTest"))
                }
            }
        }
    }

    /**
     * Stripped-down version of DefaultJavaPluginExtension.registerFeature which only registers runtime elements
     */
    fun registerFeatureForSourceSet(project: Project, featureSourceSet: SourceSet, mainSourceSet: SourceSet) {
        val projectInternal = project as ProjectInternal
        val compileJava = project.tasks.named(featureSourceSet.compileJavaTaskName, JavaCompile::class.java)
        val jvmLanguageUtilities = project.services.get(JvmLanguageUtilities::class.java)
        val jarArtifact = ArchivePublishArtifact(
            projectInternal.taskDependencyFactory,
            project.tasks.named(featureSourceSet.jarTaskName, Jar::class.java).get()
        )
        val mainRuntime = project.configurations.named(mainSourceSet.runtimeElementsConfigurationName)
        project.configurations.consumable(featureSourceSet.runtimeElementsConfigurationName) {
            jvmLanguageUtilities.useDefaultTargetPlatformInference(it, compileJava)
            jvmPluginServices.configureAsRuntimeElements(it)
            it.extendsFrom(
                project.configurations.getByName(featureSourceSet.implementationConfigurationName),
                project.configurations.getByName(featureSourceSet.runtimeOnlyConfigurationName)
            )
            it.outgoing {
                it.artifacts.add(jarArtifact)
                it.artifacts.addAllLater(mainRuntime.map { it.outgoing.artifacts })
            }
            it.attributes {
                it.addAllLater(mainRuntime.map { it.attributes }.get())
                it.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, ARCH_RULES))
            }
        }
        project.registerOutgoingVariant(
            featureSourceSet.runtimeElementsConfigurationName,
            featureSourceSet.runtimeClasspathConfigurationName,
            "runtime"
        )
    }

    /**
     * The plugin should add dependencies on the core library of the same version
     * However, there are 2 edge cases:
     * 1) tests, where jar packaging with a version has not been done
     * 2) the core library is published to maven central, whereas the plugin is published to Gradle Plugin Portal.
     *      Maven central has a much longer delay, so for a while, there is a state where the plugin ios available,
     *      but the corresponding core library is not yet available.
     *      In this case, we can match to the latest version of the same major version,
     *      which will solve the problem for any users who use dynamic minor or patch versions.
     */
    fun determineVersion(): String {
        val metadataVersion = ArchrulesLibraryPlugin::class.java.`package`.implementationVersion
        if (metadataVersion == null) {
            return "latest.release" // this happens in tests
        } else {
            val majorVersion = metadataVersion.substringBefore(".")
            return "$majorVersion.+" // in case maven central is behind GPP
        }
    }
}
