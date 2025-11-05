package com.netflix.nebula.archrules.gradle

import nebula.test.dsl.TestKitAssertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

class ArchrulesLibraryPluginTest {

    @Test
    fun `plugin registers library dependency`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(ArchrulesLibraryPlugin::class.java)
        val configuration = project.configurations.findByName("archRulesImplementation")
        assertThat(configuration).isNotNull
        val coreLibrary = configuration!!.dependencies
            .firstOrNull { it.group == "com.netflix.nebula" && it.name == "nebula-archrules-core" }
        assertThat(coreLibrary).isNotNull
        assertThat(coreLibrary!!.version).isEqualTo("latest.release")
    }
}