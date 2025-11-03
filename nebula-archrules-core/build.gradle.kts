plugins {
    id("com.netflix.nebula.library")
}
description = "Core library for authoring and running Nebula ArchRules"
repositories {
    mavenCentral()
}
dependencies {
    api("com.tngtech.archunit:archunit:1.4.1")
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
        languageVersion = JavaLanguageVersion.of(11)
    }
}
