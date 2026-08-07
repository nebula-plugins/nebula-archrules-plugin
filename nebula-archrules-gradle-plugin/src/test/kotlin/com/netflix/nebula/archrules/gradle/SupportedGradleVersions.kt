package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.Gradle

enum class SupportedGradleVersion(val version: Gradle) {
    GRADLE_9_2(Gradle.ofVersion("9.2.0")),
    CURRENT(Gradle.current()),
    GRADLE_9_7(Gradle.ofVersion("9.7.0"))
}
