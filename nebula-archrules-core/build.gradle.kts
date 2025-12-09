plugins {
    id("com.netflix.nebula.library")
}
description = "Core library for authoring and running Nebula ArchRules"
dependencies {
    api("com.tngtech.archunit:archunit:1.4.1")
    testImplementation("org.assertj:assertj-core:3.27.6")
}
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
        }
    }
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}
dependencyLocking {
    lockAllConfigurations()
}