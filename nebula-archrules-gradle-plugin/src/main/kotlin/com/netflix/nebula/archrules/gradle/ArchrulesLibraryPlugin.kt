package com.netflix.nebula.archrules.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

class ArchrulesLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withId("java") {
            val ext = project.extensions.getByType<JavaPluginExtension>()
            val archRulesSourceSet = ext.sourceSets.create("archRules")
            val version = ArchrulesLibraryPlugin::class.java.`package`.implementationVersion ?: "latest.release"
            project.dependencies.add(
                archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
            val jarTask = project.tasks.register<Jar>("archRulesJar") {
                archiveClassifier.set("archrules")
                from(archRulesSourceSet.output)
            }
            val runtimeElements = project.configurations.getByName("runtimeElements")
            runtimeElements.outgoing.variants.create("archRulesElements") {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, project.objects.named("arch-rules"))
                }
                artifact(jarTask)
            }
        }
    }
}