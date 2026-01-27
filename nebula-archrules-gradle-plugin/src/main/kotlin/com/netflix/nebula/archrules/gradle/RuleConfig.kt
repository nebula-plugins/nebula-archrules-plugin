package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.EnumSet

class RuleConfig {
    companion object {
        @JvmStatic
        val LOGGER: Logger = LoggerFactory.getLogger(RuleConfig::class.java)
    }

    var priority: Priority? = null

    fun priority(priority: String) {
        val validStrings = EnumSet.allOf(Priority::class.java).map { it.asString() }.toSet()
        if (validStrings.contains(priority)) {
            priority(Priority.valueOf(priority))
        } else {
            LOGGER.warn(
                "Invalid ArchRule priority '$priority'. " +
                        "Must be one of the following (case-sensitive): ${validStrings.joinToString(", ")}"
            )
        }
    }

    fun priority(priority: Priority) {
        this.priority = priority
    }
}
