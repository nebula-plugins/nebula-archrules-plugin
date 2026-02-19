package com.tngtech.archunit.core.importer;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.thirdparty.com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toSet;

/**
 * copy of <a href="https://github.com/TNG/ArchUnit/blob/main/archunit/src/main/java/com/tngtech/archunit/core/importer/ClassFileImporter.java">ClassFileImporter</a>
 * changed to use custom elements for package info resolution
 */
public class ClassFileImporterWithPackage {
    private static final Logger LOG = LoggerFactory.getLogger(ClassFileImporterWithPackage.class);
    private final ImportOptions importOptions;

    public ClassFileImporterWithPackage() {
        this(new ImportOptions());
    }

    public ClassFileImporterWithPackage(Collection<ImportOption> importOptions) {
        this(new ImportOptions().with(importOptions));
    }

    private ClassFileImporterWithPackage(ImportOptions importOptions) {
        this.importOptions = importOptions;
    }

    public JavaClasses importLocations(Collection<Location> locations) {
        List<ClassFileSource> sources = new ArrayList<>();
        for (Location location : locations) {
            tryAdd(sources, location);
        }
        return new ClassFileProcessorWithPackage().process(unify(sources));
    }

    public JavaClasses importPaths(String... paths) {
        return importPaths(stream(paths).map(Paths::get).collect(toSet()));
    }

    public JavaClasses importPaths(Collection<Path> paths) {
        return importLocations(paths.stream().map(Location::of).collect(toSet()));
    }

    private void tryAdd(List<ClassFileSource> sources, Location location) {
        try {
            sources.add(location.asClassFileSource(importOptions));
        } catch (Exception e) {
            LOG.warn(String.format("Couldn't derive %s from %s",
                    ClassFileSource.class.getSimpleName(), location), e);
        }
    }

    private ClassFileSource unify(List<ClassFileSource> sources) {
        return Iterables.concat(sources)::iterator;
    }
}
