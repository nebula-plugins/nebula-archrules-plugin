package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.gradle.report.GithubAnnotationJsonReportWorkAction
import com.netflix.nebula.archrules.gradle.report.fromRuleResult
import com.netflix.nebula.archrules.gradle.report.FailuresByRuleBuilder
import com.netflix.nebula.archrules.gradle.report.GithubAnnotationsReportPrinter
import com.netflix.nebula.archrules.gradle.report.SourceFileIndex
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
 * Emits GitHub Actions workflow commands and writes a JSON annotation report for rule violations.
 *
 * Source paths in both outputs are relative to [projectRoot]. The task consumes serialized
 * [RuleResult]s from [dataFiles] and uses [sourceFiles] to locate the referenced source files.
 *
 * @see [GitHub Actions workflow commands](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-commands#setting-a-notice-message)
 */
@UntrackedTask(because = "Provides console feedback")
abstract class GithubReportTask @Inject constructor(private var workerExecutor: WorkerExecutor) : DefaultTask() {

    /**
     * Binary rule-result files produced by [CheckRulesTask].
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    /**
     * Source files used to resolve violation filenames to paths relative to [projectRoot].
     * Filenames without a matching source file are retained in the annotations.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    /**
     * Base directory used to make annotation source paths relative.
     *
     * The runner sets this to the owning build's root project directory, so a child's source at
     * `app/src/main/java/Example.java` retains that path in both console and JSON annotations.
     * Custom project directories are resolved relative to the same root, rather than inferred
     * from the project's logical path.
     *
     * During task configuration, [DirectoryProperty.fileValue] converts the immutable
     * [org.gradle.api.Project.getRootDir] file path to a directory value. This avoids accessing
     * the root project's mutable layout, which is forbidden by Isolated Projects from a child.
     * The directory is only a path base; the task searches the files declared by [sourceFiles].
     */
    @get:Internal
    abstract val projectRoot: DirectoryProperty

    /**
     * Minimum violation priority included in console and JSON annotations.
     */
    @get:Input
    @get:Optional
    abstract val detailsThreshold: Property<Priority>

    /**
     * Destination for the JSON report containing the GitHub annotations.
     */
    @get:OutputFile
    abstract val githubReportFile: RegularFileProperty

    /** Runtime classpath for the isolated worker that writes the JSON report. */
    @get:Classpath
    abstract val reportingClasspath: ConfigurableFileCollection

    @TaskAction
    fun printReport() {
        val list = dataFiles.files
            .filter(File::exists)
            .flatMap { ViolationsUtil.readDetails(it) }
            .toList()
        val root = projectRoot.get().asFile
        val sources = sourceFiles
        val sourceFileIndex = SourceFileIndex(root, sources)
        GithubAnnotationsReportPrinter(System.out, sources, root, sourceFileIndex)
            .print(list, true, true, detailsThreshold.orNull)

        val annotations = FailuresByRuleBuilder.build(list)
            .mapValues { it.value.filter { it.rule().priority().meetsThreshold(detailsThreshold.get()) } }
            .filter { it.value.isNotEmpty() }
            .flatMap { it.value }
            .map { fromRuleResult(sourceFileIndex, it) }
        val workQueue: WorkQueue = workerExecutor
            .classLoaderIsolation { it.classpath.from(reportingClasspath) }
        workQueue.submit(GithubAnnotationJsonReportWorkAction::class.java) {
            it.getAnnotations().set(annotations)
            it.getJsonReportFile().set(githubReportFile.asFile)
        }
    }
}
