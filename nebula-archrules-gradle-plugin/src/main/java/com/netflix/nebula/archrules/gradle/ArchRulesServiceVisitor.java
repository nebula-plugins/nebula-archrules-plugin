package com.netflix.nebula.archrules.gradle;

import com.tngtech.archunit.thirdparty.org.objectweb.asm.ClassVisitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.tngtech.archunit.thirdparty.org.objectweb.asm.Opcodes.ASM9;

class ArchRulesServiceVisitor extends ClassVisitor {
    public List<String> getArchRuleServiceClasses() {
        return archRuleServiceClasses;
    }

    private final List<String> archRuleServiceClasses = new ArrayList<>();

    ArchRulesServiceVisitor() {
        super(ASM9);
    }

    public void visit(int version, int access, String name,
                      String signature, String superName, String[] interfaces) {
        if (Arrays.asList(interfaces).contains("com/netflix/nebula/archrules/core/ArchRulesService")) {
            archRuleServiceClasses.add(name.replace("/", "."));
        }
    }
}
