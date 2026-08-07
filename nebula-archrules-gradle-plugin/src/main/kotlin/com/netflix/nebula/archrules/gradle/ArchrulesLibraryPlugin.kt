package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.capabilities.Capability
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.internal.JavaPluginHelper
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.component.external.model.ProjectDerivedCapability
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.get
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.base.TestingExtension
import javax.inject.Inject

class ArchrulesLibraryPlugin @Inject constructor(val objects: ObjectFactory) : Plugin<Project> {

    override fun apply(project: Project) {
        val version = determineVersion()
        val archRulesUsageAttr = objects.named(Usage::class.java, ARCH_RULES)
        project.pluginManager.withPlugin("java-library") {
            project.dependencies.attributesSchema.attribute(Usage.USAGE_ATTRIBUTE) {
                compatibilityRules.add(ArchRuleUsageCompatibilityRule::class)
                disambiguationRules.add(ArchRuleUsageDisambiguationRule::class) {
                    params(objects.named(Usage::class.java, Usage.JAVA_API))
                    params(objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                    params(archRulesUsageAttr)
                }
            }
            project.dependencies.attributesSchema.attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE) {
                compatibilityRules.add(ArchRuleCompatibilityRule::class)
            }
            val javaExt = project.extensions.getByType<JavaPluginExtension>()
            val mainSourceSet = javaExt.sourceSets.getByName("main")
            val archRulesSourceSet = javaExt.sourceSets.create("archRules")
            val jarTask = project.tasks.register<Jar>("archRulesJar") {
                description = "Assembles a jar archive containing the classes of the arch rules."
                group = "build"
                manifest.from(project.tasks.named<Jar>(mainSourceSet.jarTaskName).map(Jar::getManifest).get())
                from(archRulesSourceSet.output)
                archiveClassifier.set("arch-rules")
            }
            project.tasks.named("assemble") { dependsOn(jarTask) }
            project.configurations.named(archRulesSourceSet.implementationConfigurationName).configure {
                extendsFrom(project.configurations.getByName(mainSourceSet.apiConfigurationName))
            }
            project.configurations.named(archRulesSourceSet.runtimeClasspathConfigurationName).configure {
                attributes {
                    addAllLater(
                        project.configurations.named(mainSourceSet.runtimeClasspathConfigurationName).get().attributes
                    )
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                    attribute(Usage.USAGE_ATTRIBUTE, archRulesUsageAttr)
                }
            }
            project.configurations.named(archRulesSourceSet.compileClasspathConfigurationName).configure {
                attributes {
                    addAllLater(
                        project.configurations.named(mainSourceSet.compileClasspathConfigurationName).get().attributes
                    )
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                    attribute(Usage.USAGE_ATTRIBUTE, archRulesUsageAttr)
                }
            }
            project.dependencies.add(
                archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
            registerFeatureForSourceSet(project, archRulesSourceSet, mainSourceSet)
            val generateServicesTask =
                project.tasks.register<GenerateServicesRegistryTask>("generateServicesRegistry") {
                    archRuleServicesFile.set(
                        project.layout.buildDirectory.file(
                            "resources/archRules/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"
                        )
                    )
                    ruleSourceClasses.from(project.tasks.named<JavaCompile>(archRulesSourceSet.compileJavaTaskName))
                    project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                        ruleSourceClasses.from(project.tasks.named(archRulesSourceSet.getCompileTaskName("kotlin")))
                    }
                    dependsOn(project.tasks.named(archRulesSourceSet.processResourcesTaskName))
                }
            jarTask.configure {
                dependsOn(generateServicesTask)
            }
            project.tasks.named(archRulesSourceSet.classesTaskName) {
                dependsOn(generateServicesTask)
            }
            project.tasks.register<GenerateRulesDocumentationTask>("generateRulesDocumentation") {
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
            project.pluginManager.withPlugin("jvm-test-suite") {
                val ext = project.extensions.getByType<TestingExtension>()
                ext.suites {
                    register("archRulesTest", JvmTestSuite::class.java) {
                        useJUnitJupiter()
                        dependencies {
                            implementation(project())
                            implementation(archRulesSourceSet.output)
                            implementation("com.netflix.nebula:nebula-archrules-core:$version")
                        }
                        javaExt.sourceSets.named("archRulesTest").configure {
                            project.tasks.named(compileJavaTaskName) {
                                dependsOn(generateServicesTask)
                            }
                            project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                                project.tasks.named(getCompileTaskName("kotlin")) {
                                    dependsOn(generateServicesTask)
                                }
                            }
                            project.configurations.named(implementationConfigurationName) {
                                extendsFrom(project.configurations.getByName(javaExt.sourceSets.getByName("main").implementationConfigurationName))
                            }
                            project.configurations.named(runtimeClasspathConfigurationName).configure {
                                extendsFrom(project.configurations.getByName(archRulesSourceSet.runtimeClasspathConfigurationName))
                                attributes {
                                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                                    // don't override Usage in order to not mess with junit platform engine dependencies
                                }
                            }
                            project.configurations.named(compileClasspathConfigurationName).configure {
                                extendsFrom(project.configurations.getByName(archRulesSourceSet.compileClasspathConfigurationName))
                                attributes {
                                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                                    // don't override Usage in order to not mess with junit platform engine dependencies
                                }
                            }
                        }
                    }
                }
                project.tasks.named("check") {
                    dependsOn(ext.suites.named("archRulesTest"))
                }
            }
        }
    }

    /**
     * Stripped-down version of DefaultJavaPluginExtension.registerFeature which only registers runtime elements
     */
    fun registerFeatureForSourceSet(project: Project, featureSourceSet: SourceSet, mainSourceSet: SourceSet) {
        val projectInternal = project as ProjectInternal
        val compileJava = project.tasks.named<JavaCompile>(featureSourceSet.compileJavaTaskName)
        val jvmPluginServices = project.serviceOf<JvmPluginServices>()
        val jvmLanguageUtilities = project.serviceOf<JvmLanguageUtilities>()
        val jarArtifact = ArchivePublishArtifact(
            projectInternal.taskDependencyFactory,
            project.tasks.named<Jar>(featureSourceSet.jarTaskName).get()
        )
        val mainRuntime = project.configurations.named(mainSourceSet.runtimeElementsConfigurationName)
        project.configurations.consumable(featureSourceSet.runtimeElementsConfigurationName) {
            jvmLanguageUtilities.useDefaultTargetPlatformInference(this, compileJava)
            jvmPluginServices.configureAsRuntimeElements(this)
            extendsFrom(
                project.configurations.getByName(featureSourceSet.implementationConfigurationName),
                project.configurations.getByName(featureSourceSet.runtimeOnlyConfigurationName)
            )
            outgoing {
                artifacts.add(jarArtifact)
                artifacts.addAllLater(mainRuntime.map { it.outgoing.artifacts })
            }
            attributes {
                addAllLater(mainRuntime.map { it.attributes }.get())
                attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, ARCH_RULES)
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(ARCH_RULES))
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
