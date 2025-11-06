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

#### Example

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
    private final ArchRule noDeprecated = ArchRuleDefinition.priority(Priority.LOW).noClasses()
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
will select that jar for running rules, but these rule classes will not end up up in the runtime classpath.

## LICENSE

Copyright 2025 Netflix, Inc.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.