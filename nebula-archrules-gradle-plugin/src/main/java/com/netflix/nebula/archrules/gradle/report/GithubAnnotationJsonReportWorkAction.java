package com.netflix.nebula.archrules.gradle.report;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import org.jspecify.annotations.NullMarked;
import tools.jackson.databind.json.JsonMapper;

import java.io.File;
import java.util.List;

@NullMarked
abstract public class GithubAnnotationJsonReportWorkAction implements WorkAction<GithubAnnotationJsonReportWorkAction.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(GithubAnnotationJsonReportWorkAction.class);

    public interface Parameters extends WorkParameters {
        ListProperty<GithubAnnotation> getAnnotations();

        Property<File> getJsonReportFile();
    }

    @Override
    public void execute() {
        final var report = new JsonReportRoot(getParameters().getAnnotations().get());
        new JsonMapper().writeValue(getParameters().getJsonReportFile().get(), report);
        LOGGER.lifecycle("ArchRules github annotations are available at: " + getParameters().getJsonReportFile().get().toURI());
    }

    record JsonReportRoot(List<GithubAnnotation> annotations) {
    }
}
