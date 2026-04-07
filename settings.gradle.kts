pluginManagement {
    plugins {
        id("com.netflix.nebula.root") version ("25.+")
        id("com.netflix.nebula.plugin-plugin") version ("25.+")
        id("com.netflix.nebula.library") version ("25.+")
        id("com.netflix.nebula.oss.settings") version("25.+")
    }
}
plugins {
    id("com.netflix.nebula.oss.settings")
}

rootProject.name = "nebula-archrules-plugin"

include(":nebula-archrules-core")
include(":nebula-archrules-gradle-plugin")
