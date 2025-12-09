# Nebula ArchRules

[ArchUnit](https://www.archunit.org/) a popular OSS library used to enforce “architectural” code rules as part of a
JUnit suite. However, it is limited by its design to be used as part of a JUnit suite in a single repository. Nebula
ArchRules is a toolkit which gives organizations the ability to share and apply rules across any number of repositories.
Rules can be sourced from OSS libraries or private internal libraries.

## Current Versions

### Core Library
[![Maven Central](https://img.shields.io/maven-central/v/com.netflix.nebula/nebula-archrules-core?style=for-the-badge&color=01AF01)](https://repo1.maven.org/maven2/com/netflix/nebula/nebula-archrules-core/)

### Library Plugin
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.netflix.nebula.archrules.library?style=for-the-badge&color=01AF01)](https://plugins.gradle.org/plugin/com.netflix.nebula.archrules.library)

### Runner Plugin
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.netflix.nebula.archrules.runner?style=for-the-badge&color=01AF01)](https://plugins.gradle.org/plugin/com.netflix.nebula.archrules.runner)


## Authoring Rules

To author rules, apply the ArchRules Library plugin to a project:

```kotlin
plugins {
    id("com.netflix.nebula.archrules.library") version ("latest.release")
}
```

This plugin will create a source set called `archRules`. Create classes in that source set which implement the
`com.netflix.nebula.archrules.core.ArchRulesService` interface.

#### Example (src/archRules/java/LibraryArchRulesTest.java)
```java
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.Priority;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;

import java.util.Map;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.targetOwner;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;

public class LibraryArchRules implements ArchRulesService {
    static final ArchRule noDeprecated = ArchRuleDefinition.priority(Priority.LOW).noClasses()
            .should().accessTargetWhere(targetOwner(annotatedWith(Deprecated.class)))
            .orShould().accessTargetWhere(target(annotatedWith(Deprecated.class)))
            .orShould().dependOnClassesThat().areAnnotatedWith(Deprecated.class)
            .allowEmptyShould(true)
            .as("No code should reference deprecated APIs")
            .because("usage of deprecated APIs introduces risk that future upgrades and migrations will be blocked");

    @Override
    public Map<String, ArchRule> getRules() {
        return Map.of("deprecated", noDeprecated);
    }
}
```

When authoring rules about the usage of your own library code, it is recommended to colocate your rules library in the
same project as the library code. The ArchRules plugin will publish the rules in a separate Jar, and the Runner plugin
will select that jar for running rules, but these rule classes will not end up in the runtime classpath.

You may also create a "standalone" rules library which contains only `archRules` sources, and not `main` sources. These are useful when you want to write rules about libraries you do not control. They can be applied to downstream project's `archRules` configuration so that the Runner plugin will run these rules against any source set.

#### Dependencies

You may declare dependencies on the `archRulesImplementation` configuration. This is useful for 2 use cases:
1) using a dependency as helper for your rule code
2) transitively depending on a standalone rules library so that its rules are run in any project which runs the current project's rules

### Testing Rules

The ArchRules Library plugin creates a test suite called `archRulesTest`. You can write unit tests for your rules in the `archRulesTest` source set.

#### Example (src/archRulesTest/java/LibraryArchRulesTest.java)
```java
package com.example.library;

import com.netflix.nebula.archrules.core.ArchRulesService;
import com.netflix.nebula.archrules.core.Runner;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class LibraryArchRulesTest {
    static class PassingCode {
        public void aMethod() {
            LibraryClass.newApi();
        }
    }     
    static class FailingCode {
        public void aMethod() {
            LibraryClass.deprecatedApi();
        }
    }
    
    @Test
    public void test_pass() {
        EvaluationResult result = Runner.check(LibraryArchRules.noDeprecated, PassingCode.class);
        Assertions.assertFalse(result.hasViolation());
    }
        
    @Test
    public void test_fail() {
        EvaluationResult result = Runner.check(LibraryArchRules.noDeprecated, FailingCode.class);
        Assertions.assertTrue(result.hasViolation());
    }
}
```

## Running Rules

In order to run rules in a project, add the runner plugin:
```kotlin
plugins {
    id("com.netflix.nebula.archrules.runner") version ("latest.release")
}
```

This will create a task for running rules against each source set, eg. `checkArchRulesMain` for the Main source set.
These tasks will run as dependencies of the `check` task.

If you want to run rules on all source sets, add the rule library as a dependency to the `archRules` configuration:
```kotlin
dependencies {
    archRules("your:rules:1.0.0")
}
```

Rules that exist in a library on each sourceSet's classpath will also be used:
```kotlin
dependencies {
    implementation("some.library:which-also-has-rules:1.0.0")
}
```

The plugin can generate JSON and console reports. Both are enabled by default. The console report can be disabled:
```kotlin
archRules {
    consoleReportEnabled = false
}
```

You can also disable printing summary lines for passing rules to reduce noise:
```kotlin
archRules {
    skipPassingSummaries = true
}
```

You can skip running rules on a specific source set:
```kotlin
archRules {
    skipSourceSet("test")
}
```
The `archRulesTest` source set is skipped by default.

## How it works

The Archrules Library plugin produces a separate Jar for the `archRules` sourceset, which is exposed as an alternate variant of the library. It also will automatically generate a `META-INF/services` file which contains a reference for each implementation of `com.netflix.nebula.archrules.core.ArchRulesService` to declare it as a service provider.
The Archrules Runner plugin uses a Java [ServiceLoader](https://devdocs.io/openjdk~25/java.base/java/util/serviceloader) to discover all implementations of `com.netflix.nebula.archrules.core.ArchRulesService` in the rule libraries.

## LICENSE

Copyright 2025 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.