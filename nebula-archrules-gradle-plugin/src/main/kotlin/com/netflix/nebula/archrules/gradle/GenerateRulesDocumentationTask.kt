package com.netflix.nebula.archrules.gradle

import com.netflix.nebula.archrules.core.ArchRulesService
import com.netflix.nebula.archrules.core.Runner
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.Priority
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader

@CacheableTask
abstract class GenerateRulesDocumentationTask : DefaultTask() {

    @get:Classpath
    abstract val rulesClasspath: ConfigurableFileCollection

    @get:Classpath
    abstract val dependencyClasspath: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generateDocs() {
        val rules = mutableListOf<RuleMetadata>()
        val rulesUrls = rulesClasspath.files.map { it.toURI().toURL() }.toTypedArray()
        val dependencyUrls = dependencyClasspath.files.map { it.toURI().toURL() }.toTypedArray()

//        val ownClassLoader = URLClassLoader(rulesUrls, javaClass.classLoader)
//        val ownArchRulesClasses = ServiceLoader.load(ArchRulesService::class.java, ownClassLoader)
//            .stream()
//            .map { it.type().name }
//            .toList()
//            .toSet()

        val ownArchRulesClasses = rulesClasspath.files.map {
            File(
                it,
                "META-INF/services/com.netflix.nebula.archrules.core.ArchRulesService"
            )
        }
            .firstOrNull { it.exists() }
            ?.readLines()
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()

        URLClassLoader(rulesUrls + dependencyUrls, this.javaClass.classLoader).use { classLoader ->
            ServiceLoader.load(ArchRulesService::class.java, classLoader)
                .filter { it.javaClass.name in ownArchRulesClasses }
                .forEach { service ->
                val serviceClassName = service.javaClass.name
                service.rules.forEach { (ruleName, archRule) ->
                    rules.add(
                        RuleMetadata(
                            ruleClass = serviceClassName,
                            ruleName = ruleName,
                            description = archRule.description,
                            priority = getPriority(archRule)
                        )
                    )
                }
            }
        }

        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(formatMarkdown(rules))
    }

    private fun formatMarkdown(rules: List<RuleMetadata>): String {
        val str = StringBuilder()
        str.append("# ArchRules Documentation\n\n")
        str.append("List of all archrules defined in this library.\n\n")

        val sortedRulesByName = rules.sortedBy { it.ruleName }
        sortedRulesByName.forEach { rule ->
            str.append("## ${rule.ruleName}\n\n")
            str.append("**Description:** ${rule.description}\n\n")
            str.append("**Priority:** ${rule.priority}\n\n")
            str.append("**Class:** `${rule.ruleClass}`\n\n")
            str.append("---\n\n")
        }

        return str.toString()
    }

    // workaround since ArchRule priority is private
    private fun getPriority(archRule: ArchRule): Priority {
        val dummyResult = Runner.check(archRule)
        return dummyResult.priority
    }

    private data class RuleMetadata(
        val ruleClass: String,
        val ruleName: String,
        val description: String,
        val priority: Priority
    )
}
