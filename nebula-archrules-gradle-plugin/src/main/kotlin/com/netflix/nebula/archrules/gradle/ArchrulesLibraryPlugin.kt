package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.artifacts.dsl.LazyPublishArtifact
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.plugins.jvm.internal.JvmLanguageUtilities
import org.gradle.api.plugins.jvm.internal.JvmPluginServices
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.testing.base.TestingExtension

class ArchrulesLibraryPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val version = determineVersion()
        project.pluginManager.withPlugin("java") {
            val javaExt = project.extensions.getByType<JavaPluginExtension>()
            val archRulesSourceSet = javaExt.sourceSets.create("archRules")
            project.configurations.named(archRulesSourceSet.implementationConfigurationName).configure {
                extendsFrom(project.configurations.getByName(javaExt.sourceSets.getByName("main").implementationConfigurationName))
            }
            project.configurations.named(archRulesSourceSet.runtimeClasspathConfigurationName).configure {
                attributes {
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                }
            }
            project.configurations.named(archRulesSourceSet.compileClasspathConfigurationName).configure {
                attributes {
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                }
            }
            project.dependencies.add(
                archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
            val generateServicesTask =
                project.tasks.register<GenerateServicesRegistryTask>("generateServicesRegistry") {
                    archRuleServicesFile.set(
                        project.layout.buildDirectory.file(
                            "resources/archRules/META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"
                        ).map { it.asFile }
                    )
                    ruleSourceClasses.setFrom(archRulesSourceSet.output)
                    dependsOn(archRulesSourceSet.classesTaskName)
                }
            project.tasks.named(archRulesSourceSet.classesTaskName) {
                finalizedBy(generateServicesTask)
            }
            project.tasks.named("processArchRulesResources") {
                finalizedBy(generateServicesTask)
            }
            val jarTask = project.tasks.register<Jar>("archRulesJar") {
                description = "Assembles a jar archive containing the classes of the arch rules."
                group = "build"
                from(archRulesSourceSet.output, javaExt.sourceSets.getByName("main").output)
                archiveClassifier.set("arch-rules")
                dependsOn(generateServicesTask)
            }
            registerRuntimeFeatureForSourceSet(project, archRulesSourceSet, jarTask)
            project.pluginManager.withPlugin("jvm-test-suite") {
                val ext = project.extensions.getByType<TestingExtension>()
                ext.suites {
                    register("archRulesTest", JvmTestSuite::class.java) {
                        useJUnitJupiter()
                        dependencies {
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
                                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                                }
                            }
                            project.configurations.named(compileClasspathConfigurationName).configure {
                                extendsFrom(project.configurations.getByName(archRulesSourceSet.compileClasspathConfigurationName))
                                attributes {
                                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
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
    fun registerRuntimeFeatureForSourceSet(project: Project, sourceSet: SourceSet, jarTask: TaskProvider<Jar>) {
        val component = project.components.withType<JvmSoftwareComponentInternal>().firstOrNull()
        if (component != null) {
            val compileJava = project.tasks.named<JavaCompile>(sourceSet.compileJavaTaskName)
            val jvmPluginServices = project.serviceOf<JvmPluginServices>()
            val jvmLanguageUtilities = project.serviceOf<JvmLanguageUtilities>()

            val projectInternal = project as ProjectInternal
            val jarArtifact = LazyPublishArtifact(
                jarTask,
                projectInternal.fileResolver,
                projectInternal.taskDependencyFactory
            )

            project.configurations.consumable("archRulesRuntimeElements") {
                jvmLanguageUtilities.useDefaultTargetPlatformInference(this, compileJava)
                jvmPluginServices.configureAsRuntimeElements(this)

                extendsFrom(
                    project.configurations.getByName(sourceSet.implementationConfigurationName),
                    project.configurations.getByName(sourceSet.runtimeOnlyConfigurationName)
                )
                outgoing.artifacts.add(jarArtifact);
                attributes {
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                    attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                }
            }
            val adhocComponent = component as AdhocComponentWithVariants
            adhocComponent.addVariantsFromConfiguration(
                project.configurations.getByName("archRulesRuntimeElements"),
                JavaConfigurationVariantMapping(
                    "runtime", true,
                    project.configurations.getByName("archRulesRuntimeClasspath")
                )
            )
        }
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
