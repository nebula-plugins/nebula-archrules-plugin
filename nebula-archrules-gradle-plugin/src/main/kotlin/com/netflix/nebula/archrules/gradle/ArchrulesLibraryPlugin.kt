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
        val version = ArchrulesLibraryPlugin::class.java.`package`.implementationVersion ?: "latest.release"
        project.pluginManager.withPlugin("java") {
            val ext = project.extensions.getByType<JavaPluginExtension>()
            val archRulesSourceSet = ext.sourceSets.create("archRules")
            project.dependencies.add(
                archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
            val jarTask = project.tasks.register<Jar>("archRulesJar") {
                description = "Assembles a jar archive containing the classes of the arch rules."
                group = "build"
                from(archRulesSourceSet.output)
                archiveClassifier.set("arch-rules")
            }
            registerRuntimeFeatureForSourceSet(project, archRulesSourceSet, jarTask)
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
}
