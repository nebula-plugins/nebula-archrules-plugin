package com.netflix.nebula.archrules.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NonNull;

import java.io.File;

public interface RunRulesParams extends WorkParameters {
    ConfigurableFileCollection getClassesToCheck();

    Property<@NonNull File> getDataOutputFile();
}
