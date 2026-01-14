package com.netflix.nebula.archrules.gradle

import com.tngtech.archunit.lang.Priority

fun Priority.meetsThreshold(threshold: Priority?): Boolean {
    return when (threshold) {
        null -> true
        Priority.LOW -> true
        Priority.MEDIUM -> this != Priority.LOW
        Priority.HIGH -> this == Priority.HIGH
    }
}