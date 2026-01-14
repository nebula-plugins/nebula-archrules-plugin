package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.logging.text.StyledTextOutputFactory
import org.gradle.kotlin.dsl.support.get
import java.io.File

/**
 * Prints summary and detail information about {@link RuleResult}s to the console
 */
@UntrackedTask(because = "Provides console feedback to the user")
abstract class PrintConsoleReportTask : DefaultTask() {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ListProperty<File>

    /**
     * if summary lines for passing rules should print
     */
    @get:Input
    abstract val summaryForPassingDisabled: Property<Boolean>

    /**
     * the priority threshold for printing failure details
     */
    @get:Input
    @get:Optional
    abstract val detailsThreshold: Property<Priority>

    @TaskAction
    fun printReport() {
        val consoleOutput = services.get<StyledTextOutputFactory>().create("archrules")
        val list = dataFiles.get()
            .filter(File::exists)
            .flatMap { ViolationsUtil.readDetails(it) }
            .toList()
        val byRule = ViolationsUtil.consolidatedFailures(list)
        ViolationsUtil.printSummary(byRule, consoleOutput, summaryForPassingDisabled.get())
        if (list.any {
                it.status() == RuleResultStatus.FAIL
                        && !it.rule().priority().meetsThreshold(detailsThreshold.orNull)
                        && !logger.isInfoEnabled
            }) {
            consoleOutput.style(StyledTextOutput.Style.Header)
                .text("Note: ")
                .style(StyledTextOutput.Style.Normal)
                .println("In order to see details of ${detailsThreshold.orElse(Priority.LOW)} and lower priority rules, run build with --info")
        }
        ViolationsUtil.printReport(byRule, consoleOutput, detailsThreshold.orNull, logger.isInfoEnabled)
    }
}
