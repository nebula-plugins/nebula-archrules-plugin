package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassReader;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.jspecify.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Generates a file in META-INF/services to allow Rule classes to be discovered by the Runner
 */
@CacheableTask
abstract public class GenerateServicesRegistryTask extends DefaultTask {
    /**
     * The classes declared in the archRules source set
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public ConfigurableFileCollection getRuleSourceClasses();

    /**
     * The file in META-INF/services to output to. It should be named com.netflix.nebula.archrules.core.ArchRulesService.
     */
    @OutputFile
    abstract public Property<@NonNull File> getArchRuleServicesFile();

    @TaskAction
    public void generate() throws IOException {
        ArchRulesServiceVisitor visitor = new ArchRulesServiceVisitor();
        getRuleSourceClasses().getAsFileTree().getFiles()
                .stream()
                .filter(it -> it.getName().endsWith(".class"))
                .forEach(classFile -> {
                    try {
                        if(getLogger().isDebugEnabled()) {
                            getLogger().debug("Generating archive rules for {}", classFile.getName());
                        }
                        ClassReader cr = new ClassReader(Files.newInputStream(classFile.toPath(), StandardOpenOption.READ));
                        cr.accept(visitor, ClassReader.SKIP_DEBUG);
                    } catch (IOException e) {
                        getLogger().warn("Failed to read class file {}", classFile.getAbsolutePath(), e);
                    }
                });

        getArchRuleServicesFile().get().createNewFile();
        String fileContent = String.join("\n", visitor.getArchRuleServiceClasses());
        Files.writeString(getArchRuleServicesFile().get().toPath(), fileContent, StandardOpenOption.WRITE);
    }
}
