package com.netflix.nebula.archrules.gradle;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NullMarked;

import java.io.File;

@NullMarked
public interface GenerateDocsParams extends WorkParameters {
    ListProperty<String> getOwnArchRulesClasses();

    Property<File> getOutputFile();

    Property<String> getLibraryName();
}
