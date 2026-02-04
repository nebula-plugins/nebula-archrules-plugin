package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test

internal class RunRulesWorkActionTest {
    private fun params(config: RunRulesParams.() -> Unit): RunRulesParams {
        return ProjectBuilder.builder().build().objects.newInstance(RunRulesParams::class.java).apply(config)
    }

    private fun instance(params: RunRulesParams): RunRulesWorkAction {
        return object : RunRulesWorkAction() {
            override fun getParameters(): RunRulesParams {
                return params
            }
        }
    }

    @Test
    fun `test excluded rule class exact match`() {
        val params = params {
            excludedRuleClasses.add("test")
        }
        val instance = instance(params)
        assertThat(instance.isRuleClassExcluded("test")).isTrue()
    }

    @Test
    fun `test excluded rule class fuzzy match`() {
        val params = params {
            excludedRuleClasses.add("com.netflix")
        }
        val instance = instance(params)
        assertThat(instance.isRuleClassExcluded("com.netflix.myrules.MyRuleClass")).isTrue()
    }

    @Test
    fun `test multiple class matches`() {
        val params = params {
            priorityOverridesByClass.put("com.netflix", Priority.LOW)
            priorityOverridesByClass.put("com.netflix.important", Priority.HIGH)
        }
        val instance = instance(params)
        assertThat(instance.getPriorityOverride("com.netflix.important.ImportantClass", "whatever id"))
            .hasValue(Priority.HIGH)
    }
}
