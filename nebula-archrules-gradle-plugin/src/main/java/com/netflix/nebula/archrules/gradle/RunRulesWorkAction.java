package com.netflix.nebula.archrules.gradle;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporterWithPackage;
import com.tngtech.archunit.lang.Priority;
import org.gradle.workers.WorkAction;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.netflix.nebula.archrules.core.NoClassesMatchedEvent.NO_MATCH_MESSAGE;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;

@NullMarked
public abstract class RunRulesWorkAction implements WorkAction<RunRulesParams> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunRulesWorkAction.class);

    boolean isRuleClassExcluded(String ruleClassName) {
        for (String exclusion : getParameters().getExcludedRuleClasses().get()) {
            if (ruleClassName.startsWith(exclusion)) {
                return true;
            }
        }
        return false;
    }

    Optional<Priority> getPriorityOverride(String ruleClassName, String ruleId) {
        Priority priority = null;

        // check for exact rule match first
        final var overridesByName = getParameters().getPriorityOverridesByName().getOrElse(Map.of());
        if (overridesByName.containsKey(ruleId)) {
            priority = overridesByName.get(ruleId);
        }

        if (priority == null) { // if no exact rule match, maybe there is a class-level match
            String classNameMatch = "";
            final var overridesByClass = getParameters().getPriorityOverridesByClass().getOrElse(Map.of());
            for (Map.Entry<String, Priority> override : overridesByClass.entrySet()) {
                String overrideRuleName = override.getKey();
                if (ruleClassName.startsWith(overrideRuleName)) {
                    if (overrideRuleName.length() > classNameMatch.length()) { // prefer more specific
                        priority = override.getValue();
                        classNameMatch = overrideRuleName;
                    }
                }
            }
        }

        return Optional.ofNullable(priority);
    }

    @Override
    public void execute() {
        ServiceLoader<ArchRulesService> ruleClasses = ServiceLoader.load(ArchRulesService.class);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Rule classes detected: {}", ruleClasses.stream()
                    .map(it -> it.type().getCanonicalName())
                    .collect(Collectors.joining(",")));
        }
        final var classesToCheck = new ClassFileImporterWithPackage()
                .importPaths(getParameters().getClassesToCheck().getFiles().stream().map(File::toPath).toList());
        final List<RuleResult> violationList = new ArrayList<>();

        ruleClasses.forEach(ruleClass -> {
            String ruleClassName = ruleClass.getClass().getCanonicalName();
            if (isRuleClassExcluded(ruleClassName)) {
                LOGGER.info("Rule class {} has been excluded for this source set", ruleClass.getClass().getName());
            } else {
                ruleClass.getRules().forEach((id, archRule) -> {
                    if (getParameters().getExcludedRules().get().contains(id)) {
                        LOGGER.info("Rule {} has been excluded for this source set", id);
                    } else {
                        final var predicates = getParameters().getPredicatesByName().orElse(Map.of()).get().getOrDefault(id, List.of());

                        var classesToCheckForRule = classesToCheck;
                        for (var predicate : predicates) {
                            classesToCheckForRule = classesToCheckForRule
                                .that(convertPredicate(predicate));
                        }

                        // TODO Remove debug prints
                        System.out.println("RULE: " + id);
                        System.out.println("PREDICATES: " + predicates);
                        System.out.println("CLASSES: " + classesToCheckForRule);

                        final var result = Runner.check(archRule, classesToCheckForRule);

                        // check if there is priority override by class first
                        var priority = getPriorityOverride(ruleClassName, id).orElse(result.getPriority());

                        final var rule = new Rule(ruleClassName, id, archRule.getDescription(), priority);
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
                    }
                });
            }
        });

        ViolationsUtil.writeDetails(getParameters().getDataOutputFile().get(), violationList);
    }

    private DescribedPredicate<JavaClass> convertPredicate(ArchrulesPredicate predicate) {
        class ConvertingVisitor implements ArchrulesPredicateVisitor<DescribedPredicate<JavaClass>> {

            @Override
            public DescribedPredicate<JavaClass> visitNot(ArchrulesPredicate.NotPredicate predicate) {
                return not(predicate.getPredicate().accept(this));
            }

            @Override
            public DescribedPredicate<JavaClass> visitSimpleName(ArchrulesPredicate.SimpleNamePredicate predicate) {
                return simpleName(predicate.getName());
            }

        }

        return predicate.accept( new ConvertingVisitor());
    }

}
