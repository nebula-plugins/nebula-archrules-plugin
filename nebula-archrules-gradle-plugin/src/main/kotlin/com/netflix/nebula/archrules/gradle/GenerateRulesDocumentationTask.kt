package com.netflix.nebula.archrules.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class GenerateRulesDocumentationTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @get:Classpath
    abstract val rulesClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generateDocs() {
        val ownArchRulesClasses = rulesClasspath.files.map {
            File(
                it,
                "META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"
            )
        }
            .firstOrNull { it.exists() }
            ?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()

        val workQueue: WorkQueue = workerExecutor.classLoaderIsolation {
            classpath.from(rulesClasspath)
        }

        workQueue.submit(GenerateDocsWorkAction::class) {
            getOwnArchRulesClasses().set(ownArchRulesClasses)
            getOutputFile().set(this@GenerateRulesDocumentationTask.outputFile.get().asFile)
        }
    }
}
