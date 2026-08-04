package com.netflix.nebula.archrules.gradle.report

import com.netflix.nebula.archrules.gradle.Rule
import com.netflix.nebula.archrules.gradle.RuleResult
import com.netflix.nebula.archrules.gradle.RuleResultStatus
import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

internal class GithubAnnotationsReportPrinterTest {

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `test print with failures`() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(
                RuleResult(
                    rule,
                    "Method <com.netflix.nebula.archrules.gradle.ArchrulesLibraryPlugin.registerRuntimeFeatureForSourceSet(org.gradle.api.Project, org.gradle.api.tasks.SourceSet, org.gradle.api.tasks.TaskProvider)> calls method <org.gradle.api.internal.project.ProjectInternal.getConfigurations()> in (ArchrulesLibraryPlugin.kt:193)",
                    RuleResultStatus.FAIL
                )
            )
            val instance = GithubAnnotationsReportPrinter(
                it,
                listOf(projectDir.resolve("src/main/ArchrulesLibraryPlugin.kt")),
                projectDir
            )
            instance.print(results, false, true, null)
            assertThat(it.toString(StandardCharsets.UTF_8))
                .startsWith("::warning file=src/main/ArchrulesLibraryPlugin.kt,line=193,title=description::Method ")
        }
    }

    @Test
    fun `line number defaults to 1`() {
        ByteArrayOutputStream().use {
            val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
            val results = listOf(
                RuleResult(
                    rule,
                    "Method <com.netflix.nebula.archrules.gradle.ArchrulesLibraryPlugin.registerRuntimeFeatureForSourceSet(org.gradle.api.Project, org.gradle.api.tasks.SourceSet, org.gradle.api.tasks.TaskProvider)> calls method <org.gradle.api.internal.project.ProjectInternal.getConfigurations()> in (ArchrulesLibraryPlugin.kt:0)",
                    RuleResultStatus.FAIL
                )
            )
            val instance = GithubAnnotationsReportPrinter(
                it,
                listOf(projectDir.resolve("src/main/ArchrulesLibraryPlugin.kt")),
                projectDir
            )
            instance.print(results, false, true, null)
            assertThat(it.toString(StandardCharsets.UTF_8))
                .startsWith("::warning file=src/main/ArchrulesLibraryPlugin.kt,line=1,title=description::Method ")
        }
    }
}
