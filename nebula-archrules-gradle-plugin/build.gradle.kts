import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("com.netflix.nebula.plugin-plugin")
    `kotlin-dsl`
}
description = "Plugins for authoring and running Nebula ArchRules"
dependencies {
    implementation(project(":nebula-archrules-core"))
    compileOnly("tools.jackson.core:jackson-databind:3.1.0") // keep in sync with ArchrulesRunnerPlugin

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
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("runner") {
            id = "com.netflix.nebula.archrules.runner"
            implementationClass = "com.netflix.nebula.archrules.gradle.ArchrulesRunnerPlugin"
            displayName = "ArchRules Runner Plugin"
            description = "Sets up a project to consume archrules libraries and run them against the code in the current project"
            tags.addAll("nebula", "archunit")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
        create("aggregate") {
            id = "com.netflix.nebula.archrules.aggregate"
            implementationClass = "com.netflix.nebula.archrules.gradle.ArchrulesAggregateConsoleReportPlugin"
            displayName = "ArchRules Aggregate Console Report Plugin"
            description = "Consolidates console reports for multiple subprojects"
            tags.addAll("nebula", "archunit")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
testing {
    suites {
        named<JvmTestSuite>("test") {
            useJUnitJupiter()
            targets.all {
                testTask.configure {
                    maxParallelForks = 4
                }
            }
        }
    }
}
dependencyLocking {
    lockAllConfigurations()
}
configurations.named("testArchRulesRuntime").configure {
    resolutionStrategy.dependencySubstitution {
        // workaround for classpath issue
        all {
            val requestedProject = requested
            if (requestedProject is ProjectComponentSelector) {
                useTarget("com.netflix.nebula" + requestedProject.projectPath + ":latest.release")
            }
        }
    }
}
configurations.named("mainArchRulesRuntime").configure {
    resolutionStrategy.dependencySubstitution {
        // workaround for classpath issue
        all {
            val requestedProject = requested
            if (requestedProject is ProjectComponentSelector) {
                useTarget("com.netflix.nebula" + requestedProject.projectPath + ":latest.release")
            }
        }
    }
}
archRules {
    ruleName("javaxRule") {
        priority("LOW") // Gradle still requires use of javax Inject
    }
}
kotlin {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}
