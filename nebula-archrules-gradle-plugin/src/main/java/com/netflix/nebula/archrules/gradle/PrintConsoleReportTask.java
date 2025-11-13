package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.util.List;

/**
 * Prints summary and detail information about {@link RuleResult}s to the console
 */
@UntrackedTask(because = "Provides console feedback to the user")
abstract public class PrintConsoleReportTask extends DefaultTask {

    /**
     * The data files to read in. These files should container binary data representing {@link RuleResult}s
     * @return all data files to process
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public ListProperty<@NonNull File> getDataFiles();

    @TaskAction
    public void printReport() {
        final var consoleOutput = getServices().get(StyledTextOutputFactory.class).create("archrules");
        consoleOutput.style(StyledTextOutput.Style.Header).println("ArchRule summary:").println();
        List<RuleResult> list = getDataFiles().get().stream()
                .flatMap(it -> ViolationsUtil.readDetails(it).stream())
                .toList();
        final var byRule = ViolationsUtil.consolidatedFailures(list);
        ViolationsUtil.printSummary(byRule, consoleOutput);
        consoleOutput.println();
        if (list.stream().anyMatch(it -> it.status() != RuleResultStatus.FAIL && it.rule().priority() == Priority.LOW) && !getLogger().isInfoEnabled()) {
            consoleOutput.style(StyledTextOutput.Style.Header)
                    .text("Note: ")
                    .style(StyledTextOutput.Style.Normal)
                    .println("In order to see details of LOW priority rules, run build with --info")
                    .println();
        }
        ViolationsUtil.printReport(byRule, consoleOutput, getLogger().isInfoEnabled());
    }
}
