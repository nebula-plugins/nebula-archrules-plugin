package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

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
}
