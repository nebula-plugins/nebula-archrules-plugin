package com.netflix.nebula.archrules.gradle;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.lang.Priority;
import org.gradle.workers.WorkAction;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceLoader;

@NullMarked
public abstract class GenerateDocsWorkAction implements WorkAction<GenerateDocsParams> {

    @Override
    public void execute() {
        var ownArchRulesClasses = new HashSet<>(getParameters().getOwnArchRulesClasses().get());

        var rules = new ArrayList<RuleMetadata>();
        ServiceLoader.load(ArchRulesService.class).forEach(service -> {
            var className = service.getClass().getName();
            if (ownArchRulesClasses.contains(className)) {
                service.getRules().forEach((ruleName, archRule) -> {
                    rules.add(new RuleMetadata(
                            className,
                            ruleName,
                            archRule.getDescription(),
                            Runner.check(archRule).getPriority()
                    ));
                });
            }
        });

        var output = getParameters().getOutputFile().get();
        output.getParentFile().mkdirs();
        try {
            Files.writeString(output.toPath(), formatMarkdown(rules));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String formatMarkdown(List<RuleMetadata> rules) {
        var str = new StringBuilder();
        str.append("# ArchRules Documentation\n\n");
        str.append("List of all archrules defined in this library.\n\n");

        var sortedRules = rules.stream().sorted(Comparator.comparing(r -> r.ruleName)).toList();
        sortedRules.forEach(rule -> {
            str.append("## ").append(rule.ruleName).append("\n\n");
            str.append("**Description:** ").append(rule.description).append("\n\n");
            str.append("**Priority:** ").append(rule.priority).append("\n\n");
            str.append("**Class:** `").append(rule.ruleClass).append("`\n\n");
            str.append("---\n\n");
        });

        return str.toString();
    }

    private record RuleMetadata(String ruleClass, String ruleName, String description, Priority priority) {}
}
