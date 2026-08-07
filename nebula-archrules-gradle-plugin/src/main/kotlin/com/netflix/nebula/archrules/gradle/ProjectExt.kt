package com.netflix.nebula.archrules.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping
import org.gradle.kotlin.dsl.named

fun Project.registerOutgoingVariant(
    consumableConfigurationName: String,
    configurationForDependenciesName: String,
    scope: String
) {
    project.components.named<AdhocComponentWithVariants>("java") {
        addVariantsFromConfiguration(
            project.configurations.named<ConsumableConfiguration>(consumableConfigurationName),
            JavaConfigurationVariantMapping(
                scope,
                true,
                project.configurations.getByName(configurationForDependenciesName)
            )
        )
    }
}
