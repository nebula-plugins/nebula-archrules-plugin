package com.netflix.nebula.archrules.gradle;

import com.netflix.nebula.archrules.gradle.report.FailuresByRuleBuilder;
import com.netflix.nebula.archrules.gradle.report.ResultsByRuleBuilder;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@NullMarked
abstract public class JsonReportWorkAction implements WorkAction<JsonReportWorkAction.JsonReportWorkParameters> {
    private final Logger logger = Logging.getLogger(JsonReportWorkAction.class);

    interface JsonReportWorkParameters extends WorkParameters {
        ListProperty<File> getDataFiles();

        Property<File> getJsonReportFile();
    }

    @Override
    public void execute() {
        JsonReportWorkParameters params = getParameters();
        List<RuleResult> list = params.getDataFiles().get().stream()
                .filter(File::exists)
                .flatMap(it -> ViolationsUtil.readDetails(it).stream())
                .toList();
        File file = params.getJsonReportFile().get();
        try (OutputStream os = new FileOutputStream(file)) {
            print(list, os);
        } catch (IOException e) {
            logger.warn("Error printing archrules json report", e);
        }
    }

    static void print(List<RuleResult> results, OutputStream outputStream) {
        Map<Rule, List<RuleResult>> byRule = ResultsByRuleBuilder.build(results);
        final var report = new JsonReportRoot(byRule.values().stream().flatMap(Collection::stream).toList());
        new JsonMapper().writeValue(outputStream, report);
    }

    record JsonReportRoot(List<RuleResult> violations) {
    }
}
