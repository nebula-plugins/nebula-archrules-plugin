package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.report.GithubAnnotationsReportPrinter
import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File

/**
 * Prints detail information about {@link RuleResult}s to the console for github annotations
 * @see: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#setting-a-notice-message
 */
@UntrackedTask(because = "Provides console feedback")
abstract class GithubReportTask : DefaultTask() {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectRoot: DirectoryProperty

    /**
     * the priority threshold for printing failure details
     */
    @get:Input
    @get:Optional
    abstract val detailsThreshold: Property<Priority>

    /**
     * File to output gihub annotations to
     * @return file for output
     */
    @get:OutputFile
    abstract val githubReportFile: RegularFileProperty

    @TaskAction
    fun printReport() {
        val list = dataFiles.files
            .filter(File::exists)
            .flatMap { ViolationsUtil.readDetails(it) }
            .toList()
        GithubAnnotationsReportPrinter(System.out, sourceFiles, projectRoot.get().asFile)
            .print(list, true, true, detailsThreshold.orNull)
        githubReportFile.get().asFile.outputStream().use {
            GithubAnnotationsReportPrinter(it, sourceFiles, projectRoot.get().asFile)
                .print(list, true, true, detailsThreshold.orNull)
        }
        logger.lifecycle("ArchRules github annotations are available at: " + githubReportFile.get().asFile.toURI())
    }
}
