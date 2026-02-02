package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class CheckRulesTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rulesClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcesToCheck: ConfigurableFileCollection

    @get:OutputFile
    abstract val dataFile: Property<File>

    @get:Input
    abstract val priorityOverridesByName: MapProperty<String, Priority>

    @get:Input
    abstract val priorityOverridesByClass: MapProperty<String, Priority>

    @TaskAction
    fun checkRules() {
        val workQueue: WorkQueue = workerExecutor.classLoaderIsolation {
            classpath.from(rulesClasspath)
        }
        workQueue.submit(RunRulesWorkAction::class) {
            getClassesToCheck().from(sourcesToCheck)
            getDataOutputFile().set(dataFile)
            getPriorityOverridesByName().set(this@CheckRulesTask.priorityOverridesByName)
            getPriorityOverridesByClass().set(this@CheckRulesTask.priorityOverridesByClass)
        }
    }
}
