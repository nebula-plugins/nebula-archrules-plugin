plugins {
    id("com.netflix.nebula.plugin-plugin")
    `kotlin-dsl`
}
description = "Plugins for authoring and running Nebula ArchRules"
repositories {
    mavenCentral()
}
dependencies {
    implementation(project(":nebula-archrules-core"))
    testImplementation("net.javacrumbs.json-unit:json-unit-assertj:5.0.0")
    testImplementation("org.json:json:20250517")
}
gradlePlugin {
    plugins {
        create("library") {
            id = "com.netflix.nebula.archrules.library"
            implementationClass = "com.netflix.nebula.archrules.gradle.ArchrulesLibraryPlugin"
            displayName = "ArchRules Library Plugin"
            description = "Sets up a project for declaring archrules to be used in another project via the runner plugin"
            tags.addAll("nebula", "archunit")
        }
        create("runner") {
            id = "com.netflix.nebula.archrules.runner"
            implementationClass = "com.netflix.nebula.archrules.gradle.ArchrulesRunnerPlugin"
            displayName = "ArchRules Runner Plugin"
            description = "Sets up a project to consume archrules libraries and run them against the code in the current project"
            tags.addAll("nebula", "archunit")
        }
    }
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
testing {
    suites{
        named<JvmTestSuite>("test"){
            useJUnitJupiter()
        }
    }
}
dependencyLocking {
    lockAllConfigurations()
}