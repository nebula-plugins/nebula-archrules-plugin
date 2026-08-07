package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.submit
import org.gradle.workers.WorkQueue
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@CacheableTask
abstract class CheckRulesTask @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask() {

    @get:InputFiles
    @get:Classpath
    abstract val rulesClasspath: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourcesToCheck: ConfigurableFileCollection

    @get:OutputFile
    abstract val dataFile: RegularFileProperty

    @get:Input
    abstract val priorityOverridesByName: MapProperty<String, Priority>

    @get:Input
    abstract val priorityOverridesByClass: MapProperty<String, Priority>

    @get:Input
    abstract val excludedRules: ListProperty<String>

    @get:Input
    abstract val excludedRuleClasses: ListProperty<String>

    @get:Input
    abstract val skip: Property<Boolean>

    @TaskAction
    fun checkRules() {
        if (skip.getOrElse(false)) {
            if (dataFile.asFile.get().exists()) {
                dataFile.asFile.get().delete()
            }
        } else {
            val workQueue: WorkQueue = workerExecutor.classLoaderIsolation {
                classpath.from(rulesClasspath)
            }
            workQueue.submit(RunRulesWorkAction::class) {
                getClassesToCheck().from(sourcesToCheck)
                getDataOutputFile().set(dataFile.asFile)
                getPriorityOverridesByName().set(this@CheckRulesTask.priorityOverridesByName)
                getPriorityOverridesByClass().set(this@CheckRulesTask.priorityOverridesByClass)
                getExcludedRules().set(this@CheckRulesTask.excludedRules)
                getExcludedRuleClasses().set(this@CheckRulesTask.excludedRuleClasses)
            }
        }
    }
}
