package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class RuleConfig {
    val LOGGER: Logger = LoggerFactory.getLogger(RuleConfig::class.java)
    var priority: Priority? = null

    fun priority(priority: String) {
        try {
            this.priority = Priority.valueOf(priority)
        } catch(e: IllegalArgumentException) {
            val validValues = Priority.entries.joinToString { it.name }
            LOGGER.warn("Invalid priority '$priority'. Must be one of the following (case-sensitive): $validValues")
        }
    }

    fun priority(priority: Priority) {
        this.priority = priority
    }
}