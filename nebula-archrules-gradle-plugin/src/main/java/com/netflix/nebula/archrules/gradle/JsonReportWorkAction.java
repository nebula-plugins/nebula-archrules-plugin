package com.netflix.nebula.archrules.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.List;

@NullMarked
abstract public class JsonReportWorkAction implements WorkAction<JsonReportWorkAction.JsonReportWorkParameters> {
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

        final var report = new JsonReportRoot(list);
        new JsonMapper().writeValue(params.getJsonReportFile().get(), report);
    }

    record JsonReportRoot(List<RuleResult> violations) {
    }
}
