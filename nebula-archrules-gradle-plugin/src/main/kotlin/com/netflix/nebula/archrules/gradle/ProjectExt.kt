package com.netflix.nebula.archrules.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping

fun Project.registerOutgoingVariant(
    consumableConfigurationName: String,
    configurationForDependenciesName: String,
    scope: String
) {
    project.components.named("java", AdhocComponentWithVariants::class.java) {
        it.addVariantsFromConfiguration(
            project.configurations.named(consumableConfigurationName, ConsumableConfiguration::class.java),
            JavaConfigurationVariantMapping(
                scope,
                true,
                project.configurations.getByName(configurationForDependenciesName)
            )
        )
    }
}
