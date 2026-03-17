package com.netflix.nebula.archrules.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.NullMarked;

import javax.inject.Inject;
import java.io.File;

/**
 * Produces a JSON report of all ArchRules failures
 */
@CacheableTask
@NullMarked
abstract public class PrintJsonReportTask extends DefaultTask {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public ConfigurableFileCollection getDataFiles();

    /**
     * File to output JSON to
     * @return file for output
     */
    @OutputFile
    abstract public Property<File> getJsonReportFile();

    @Classpath
    abstract public ConfigurableFileCollection getReportingClasspath();

    @Inject
    abstract public WorkerExecutor getWorkerExecutor();

    /**
     * The action for this task
     */
    @TaskAction
    public void printReport() {
        WorkQueue workQueue = getWorkerExecutor()
                .classLoaderIsolation(workerSpec ->
                        workerSpec.getClasspath().from(getReportingClasspath()));
        workQueue.submit(JsonReportWorkAction.class, parameters -> {
            parameters.getDataFiles().set(getDataFiles());
            parameters.getJsonReportFile().set(getJsonReportFile());
        });
    }
}
