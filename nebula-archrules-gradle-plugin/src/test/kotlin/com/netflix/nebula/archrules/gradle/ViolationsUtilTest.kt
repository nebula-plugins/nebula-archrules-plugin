package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.gradle.internal.logging.text.StyledTextOutput
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

    @Test
    fun `test printSummary`() {
        val output = MockStyledTextOutput()
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
        ViolationsUtil.printSummary(mapOf(rule to results), output, false, false)
        assertThat(output.getOutput())
            .contains("RuleClass")
            .contains("RuleName  MEDIUM     (No failures)")
    }

    @Test
    fun `test printSummary skipPassing`() {
        val output = MockStyledTextOutput()
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
        ViolationsUtil.printSummary(mapOf(rule to results), output, true, false)
        assertThat(output.getOutput())
            .contains("RuleClass")
            .doesNotContain("RuleName")
    }

    @Test
    fun `test printSummary skipPassing but infoLogging on`() {
        val output = MockStyledTextOutput()
        val rule = Rule("RuleClass", "RuleName", "description", Priority.MEDIUM)
        val results = listOf(RuleResult(rule, "message", RuleResultStatus.PASS))
        ViolationsUtil.printSummary(mapOf(rule to results), output, true, true)
        assertThat(output.getOutput())
            .contains("RuleClass")
            .contains("RuleName  MEDIUM     (No failures)")
    }

    @Test
    fun `test printSummary empty results`() {
        val output = MockStyledTextOutput()
        ViolationsUtil.printSummary(mapOf(), output, false, false)
        assertThat(output.getOutput()).isEqualTo("ArchRule Summary:\n")
    }
}

class MockStyledTextOutput : StyledTextOutput {
    private val output: StringBuilder = StringBuilder()
    fun getOutput(): String = output.toString()
    override fun append(c: Char): StyledTextOutput {
        output.append(c)
        return this
    }

    override fun append(csq: CharSequence?): StyledTextOutput {
        output.append(csq)
        return this
    }

    override fun append(
        csq: CharSequence?,
        start: Int,
        end: Int
    ): StyledTextOutput {
        output.append(csq)
        return this
    }

    override fun style(style: StyledTextOutput.Style?): StyledTextOutput {
        return this
    }

    override fun withStyle(style: StyledTextOutput.Style?): StyledTextOutput {
        return this
    }

    override fun text(text: Any?): StyledTextOutput {
        output.append(text)
        return this
    }

    override fun println(text: Any): StyledTextOutput {
        output.append(text).append("\n")
        return this
    }

    override fun format(
        pattern: String?,
        vararg args: Any?
    ): StyledTextOutput {
        output.append(pattern?.format(*args))
        return this
    }

    override fun formatln(
        pattern: String?,
        vararg args: Any?
    ): StyledTextOutput {
        output.append(pattern?.format(*args)).append("\n")
        return this
    }

    override fun println(): StyledTextOutput {
        output.append("\n")
        return this
    }

    override fun exception(throwable: Throwable?): StyledTextOutput {
        TODO("Not yet implemented")
    }

}
