package com.netflix.nebula.archrules.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType

class ArchrulesLibraryPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.withId("java") {
            val ext = project.extensions.getByType<JavaPluginExtension>()
            val archRulesSourceSet = ext.sourceSets.create("archRules")
            val version = ArchrulesLibraryPlugin::class.java.`package`.implementationVersion ?: "latest.release"
            project.dependencies.add(archRulesSourceSet.implementationConfigurationName,
                "com.netflix.nebula:nebula-archrules-core:$version"
            )
        }
    }
}