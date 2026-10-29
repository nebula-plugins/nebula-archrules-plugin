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

    @Test
    fun `console and json preserve first duplicate source and missing source fallback`() {
        val sources = listOf(
            projectDir.resolve("src/test/java/Example.java"),
            projectDir.resolve("src/main/java/Example.java")
        )
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val repeated = RuleResult(rule, "Class <example.Example> violates rule in (Example.java:27)", RuleResultStatus.FAIL)
        val missing = RuleResult(rule, "Class <example.Missing> violates rule in (Missing.java:9)", RuleResultStatus.FAIL)
        val index = SourceFileIndex(projectDir, sources)
        val output = ByteArrayOutputStream()
        val printer = GithubAnnotationsReportPrinter(output, sources, projectDir, index)

        listOf(repeated, repeated, missing).forEach { printer.printRuleViolationDetail(it) }

        assertThat(output.toString(StandardCharsets.UTF_8).lines().filter { it.isNotEmpty() }).containsExactly(
            "::warning file=src/test/java/Example.java,line=27,title=description::Class <example.Example> violates rule ",
            "::warning file=src/test/java/Example.java,line=27,title=description::Class <example.Example> violates rule ",
            "::warning file=Missing.java,line=9,title=description::Class <example.Missing> violates rule "
        )
        assertThat(fromRuleResult(index, repeated).path()).isEqualTo("src/test/java/Example.java")
        assertThat(fromRuleResult(index, missing).path()).isEqualTo("Missing.java")
        assertThat(fromRuleResult(projectDir, sources, repeated)).isEqualTo(fromRuleResult(index, repeated))
    }

    @Test
    fun `passing only report does not enumerate source files`() {
        val sources = Iterable<File> { error("Sources are unnecessary without annotations") }
        val output = ByteArrayOutputStream()
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)

        GithubAnnotationsReportPrinter(output, sources, projectDir)
            .print(listOf(RuleResult(rule, "", RuleResultStatus.PASS)), false, true, null)

        assertThat(output.size()).isZero()
    }
}
