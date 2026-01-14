package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PriorityExtTest {
    @Test
    fun `test threshold null`() {
        assertThat(Priority.LOW.meetsThreshold(null)).isTrue()
        assertThat(Priority.MEDIUM.meetsThreshold(null)).isTrue()
        assertThat(Priority.HIGH.meetsThreshold(null)).isTrue()
    }

    @Test
    fun `test threshold low`() {
        assertThat(Priority.LOW.meetsThreshold(Priority.LOW)).isTrue()
        assertThat(Priority.MEDIUM.meetsThreshold(Priority.LOW)).isTrue()
        assertThat(Priority.HIGH.meetsThreshold(Priority.LOW)).isTrue()
    }

    @Test
    fun `test threshold medium`() {
        assertThat(Priority.LOW.meetsThreshold(Priority.MEDIUM)).isFalse()
        assertThat(Priority.MEDIUM.meetsThreshold(Priority.MEDIUM)).isTrue()
        assertThat(Priority.HIGH.meetsThreshold(Priority.MEDIUM)).isTrue()
    }

    @Test
    fun `test threshold high`() {
        assertThat(Priority.LOW.meetsThreshold(Priority.HIGH)).isFalse()
        assertThat(Priority.MEDIUM.meetsThreshold(Priority.HIGH)).isFalse()
        assertThat(Priority.HIGH.meetsThreshold(Priority.HIGH)).isTrue()
    }
}