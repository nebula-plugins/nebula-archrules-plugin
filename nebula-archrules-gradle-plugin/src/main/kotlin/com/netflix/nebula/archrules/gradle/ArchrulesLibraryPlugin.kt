package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.ArchRuleAttribute.ARCH_RULES
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.internal.DefaultJavaFeatureSpec
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.component.internal.JvmSoftwareComponentInternal
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
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
            registerRuntimeFeatureForSourceSet(project, archRulesSourceSet)
            project.configurations.named("archRulesRuntimeElements") {
                attributes {
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                }
            }
            project.configurations.named("archRulesApiElements") {
                attributes {
                    attribute(ArchRuleAttribute.ARCH_RULES_ATTRIBUTE, project.objects.named(ARCH_RULES))
                }
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
    fun registerRuntimeFeatureForSourceSet(project: Project, sourceSet: SourceSet) {
        val spec = DefaultJavaFeatureSpec("archRules", project as ProjectInternal)
        spec.usingSourceSet(sourceSet)
        spec.capability(project.group.toString(), project.name, project.version.toString())
        val feature = spec.create()
        val component = project.components.withType<JvmSoftwareComponentInternal>().firstOrNull()
        if (component != null) {
            component.features.add(feature)
            val adhocComponent = component as AdhocComponentWithVariants
            if (spec.isPublished) {
                adhocComponent.addVariantsFromConfiguration(
                    project.configurations.getByName("archRulesRuntimeElements"),
                    JavaConfigurationVariantMapping("runtime", true, feature.runtimeClasspathConfiguration)
                )
            }
        }
    }
}
