pluginManagement {
    plugins {
        id("com.netflix.nebula.root") version ("25.+")
        id("com.netflix.nebula.plugin-plugin") version ("25.+")
        id("com.netflix.nebula.library") version ("25.+")
    }
}
plugins {
    id("com.gradle.develocity") version("4.2")
}

develocity {
    buildScan {
        termsOfUseUrl = "https://gradle.com/terms-of-service"
        termsOfUseAgree = "yes"
    }
}

rootProject.name = "nebula-archrules-plugin"

include(":nebula-archrules-core")
include(":nebula-archrules-gradle-plugin")