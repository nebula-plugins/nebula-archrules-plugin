plugins {
    id("com.netflix.nebula.library")
}
description = "Core library for authoring and running Nebula ArchRules"
dependencies {
    api(libs.archunit)
    testImplementation("org.assertj:assertj-core:3.27.7")
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
