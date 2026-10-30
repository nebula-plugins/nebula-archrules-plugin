package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

internal class ViolationsUtilTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `test serialization`() {
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
        val dataFile = tempDir.resolve("test.data")
        ViolationsUtil.writeDetails(dataFile, results)
        val actual = ViolationsUtil.readDetails(dataFile)
        assertThat(actual).containsExactlyElementsOf(results)
    }

    @Test
    fun `result files remain byte compatible across buffer boundaries`() {
        val rules = Priority.values().map { Rule("RuleClass", "RuleName-$it", "description λ", it) }
        val results = List(2_000) { index ->
            RuleResult(rules[index % rules.size], "message $index ${"detail λ ".repeat(32)}", RuleResultStatus.values()[index % 3])
        }
        val existing = tempDir.resolve("existing.data")
        ObjectOutputStream(existing.outputStream()).use { output ->
            output.writeInt(results.size)
            results.forEach { output.writeObject(it) }
        }
        val current = tempDir.resolve("current.data")

        ViolationsUtil.writeDetails(current, results)

        assertThat(current.readBytes()).isEqualTo(existing.readBytes())
        assertThat(ViolationsUtil.readDetails(existing)).containsExactlyElementsOf(results)
        ObjectInputStream(current.inputStream()).use { input ->
            assertThat(input.readInt()).isEqualTo(results.size)
            assertThat(List(results.size) { input.readObject() as RuleResult }).containsExactlyElementsOf(results)
        }
    }
}
