package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.lang.Priority;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.util.List;

@NullMarked
public interface RunRulesParams extends WorkParameters {
    ConfigurableFileCollection getClassesToCheck();

    Property<File> getDataOutputFile();

    MapProperty<String, List<ArchrulesPredicate>> getPredicatesByName();

    MapProperty<String, Priority> getPriorityOverridesByName();

    MapProperty<String, Priority> getPriorityOverridesByClass();

    ListProperty<String> getExcludedRules();

    ListProperty<String> getExcludedRuleClasses();
}
