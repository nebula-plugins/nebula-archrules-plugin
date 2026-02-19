package com.tngtech.archunit.core.domain;

import java.util.Arrays;

/**
 * logic from <a href="https://github.com/TNG/ArchUnit/pull/1565">package dependency scanning</a>
 */
public class DomainObjectCreationContextWithPackage {

    public static void completePackage(JavaClass javaClass, ImportContext importContext) {
        JavaPackage javaPackage = JavaPackage.from(Arrays.asList(
                javaClass,
                importContext.resolveClass(javaClass.getPackageName() + ".package-info")));
        javaClass.setPackage(javaPackage);
    }
}
