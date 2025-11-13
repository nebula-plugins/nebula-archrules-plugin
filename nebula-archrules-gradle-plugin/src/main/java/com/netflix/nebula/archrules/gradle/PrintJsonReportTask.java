package com.netflix.nebula.archrules.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.List;

/**
 * Produces a JSON report of all ArchRules failures
 */
@CacheableTask
abstract public class PrintJsonReportTask extends DefaultTask {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public ListProperty<@NonNull File> getDataFiles();

    /**
     * File to output JSON to
     * @return file for output
     */
    @OutputFile
    abstract public Property<@NonNull File> getJsonReportFile();

    /**
     * The action for this task
     */
    @TaskAction
    public void printReport() {
        List<RuleResult> list = getDataFiles().get().stream()
                .flatMap(it -> ViolationsUtil.readDetails(it).stream())
                .toList();

        final var report = new JsonReportRoot(list);
        new JsonMapper().writeValue(getJsonReportFile().get(), report);
    }

    record JsonReportRoot(List<RuleResult> violations) {
    }
}
