package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.report.GithubAnnotationJsonReportWorkAction
import com.netflix.nebula.archrules.gradle.report.fromRuleResult
import com.netflix.nebula.archrules.gradle.report.FailuresByRuleBuilder
import com.netflix.nebula.archrules.gradle.report.GithubAnnotationsReportPrinter
import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Prints detail information about {@link RuleResult}s to the console for github annotations
 * @see: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#setting-a-notice-message
 */
@UntrackedTask(because = "Provides console feedback")
abstract class GithubReportTask @Inject constructor(private var workerExecutor: WorkerExecutor) : DefaultTask() {

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
     * File to output github annotations json to
     * @return file for output
     */
    @get:OutputFile
    abstract val githubReportFile: RegularFileProperty

    @get:Classpath
    abstract val reportingClasspath: ConfigurableFileCollection

    @TaskAction
    fun printReport() {
        val list = dataFiles.files
            .filter(File::exists)
            .flatMap { ViolationsUtil.readDetails(it) }
            .toList()
        GithubAnnotationsReportPrinter(System.out, sourceFiles, projectRoot.get().asFile)
            .print(list, true, true, detailsThreshold.orNull)

        val annotations = FailuresByRuleBuilder.build(list)
            .mapValues { it.value.filter { it.rule().priority().meetsThreshold(detailsThreshold.get()) } }
            .filter { it.value.isNotEmpty() }
            .flatMap { it.value }
            .map { fromRuleResult(projectRoot.get().asFile, sourceFiles, it) }
        val workQueue: WorkQueue = workerExecutor
            .classLoaderIsolation { classpath.from(reportingClasspath) }
        workQueue.submit(GithubAnnotationJsonReportWorkAction::class.java) {
            getAnnotations().set(annotations)
            getJsonReportFile().set(githubReportFile.asFile)
        }
    }
}
