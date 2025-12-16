package com.netflix.nebula.archrules.gradle;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.gradle.workers.WorkAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import static com.netflix.nebula.archrules.core.NoClassesMatchedEvent.NO_MATCH_MESSAGE;

public abstract class RunRulesWorkAction implements WorkAction<RunRulesParams> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRulesWorkAction.class);

    @Override
    public void execute() {
        ServiceLoader<ArchRulesService> ruleClasses = ServiceLoader.load(ArchRulesService.class);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Rule classes detected: {}", ruleClasses.stream()
                    .map(it -> it.type().getCanonicalName())
                    .collect(Collectors.joining(",")));
        }
        final var classesToCheck = new ClassFileImporter()
                .importPaths(getParameters().getClassesToCheck().getFiles().stream().map(File::toPath).toList());
        final List<RuleResult> violationList = new ArrayList<>();
        ruleClasses.forEach(ruleClass -> ruleClass.getRules().forEach((id, archRule) -> {
            final var result = Runner.check(archRule, classesToCheck);
            //TODO: allow priority overrides by rule name
            final var rule = new Rule(ruleClass.getClass().getCanonicalName(), id, archRule.getDescription(), result.getPriority());
            if (result.hasViolation()) {
                result.getFailureReport().getDetails().forEach(detail -> {
                    if (detail.equals(NO_MATCH_MESSAGE)) {
                        violationList.add(new RuleResult(rule, detail, RuleResultStatus.NO_MATCH));
                    } else {
                        violationList.add(new RuleResult(rule, detail, RuleResultStatus.FAIL));
                    }
                });
            } else {
                violationList.add(new RuleResult(rule, "", RuleResultStatus.PASS));
            }
        }));

        try (var out = new ObjectOutputStream(new FileOutputStream(getParameters().getDataOutputFile().get()))) {
            out.writeInt(violationList.size());
            violationList.forEach((v) -> {
                try {
                    out.writeObject(v);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}