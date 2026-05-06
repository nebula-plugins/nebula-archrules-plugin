package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.report.MarkdownReportPrinter
import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * Prints summary and detail information about {@link RuleResult}s to the console
 */
@UntrackedTask(because = "Provides console feedback to the user")
abstract class PrintMarkdownReportTask : DefaultTask() {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    /**
     * the priority threshold for printing failure details
     */
    @get:Input
    @get:Optional
    abstract val detailsThreshold: Property<Priority>

    /**
     * File to output markdown to
     * @return file for output
     */
    @get:OutputFile
    abstract val markdownReportFile: RegularFileProperty

    private var filteredRules: List<String> = emptyList()
    private var filteredRuleClasses: List<String> = emptyList()

    @Option(
        option = "rule-name",
        description = "Print only results for the specified rule name(s). Can be specified multiple times."
    )
    fun filterByRuleName(rules: List<String>) {
        filteredRules = rules
    }

    @Option(
        option = "rule-class",
        description = "Print only results for rule classes matching the specified prefix(es). Can be specified multiple times."
    )
    fun filterByRuleClass(ruleClasses: List<String>) {
        filteredRuleClasses = ruleClasses
    }

    @TaskAction
    fun printReport() {
        val list = dataFiles.files
            .filter(File::exists)
            .flatMap { ViolationsUtil.readDetails(it) }
            .filter {
                val noFilters = filteredRules.isEmpty() && filteredRuleClasses.isEmpty()
                val matchesRule = filteredRules.contains(it.rule().ruleName())
                val matchesClass = filteredRuleClasses.any { prefix -> it.rule().ruleClass().startsWith(prefix) }
                noFilters || matchesRule || matchesClass
            }
            .toList()
        markdownReportFile.get().asFile.outputStream().use {
            MarkdownReportPrinter(it)
                .print(list, false, true, detailsThreshold.orNull)
        }
        logger.lifecycle("ArchRules markdown report is available at: " + markdownReportFile.get().asFile.toURI())
    }
}
