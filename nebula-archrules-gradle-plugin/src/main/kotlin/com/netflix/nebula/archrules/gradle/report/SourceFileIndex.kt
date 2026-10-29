package com.netflix.nebula.archrules.gradle.report

import java.io.File

/** Owned by one report execution; preserves the first matching source in input order. */
internal class SourceFileIndex(private val projectRoot: File, private val sourceFiles: Iterable<File>) {
    private val filesByName by lazy(LazyThreadSafetyMode.NONE) { sourceFiles.groupBy { it.name } }
    private val pathsByName = mutableMapOf<String, String>()

    fun pathFor(fileName: String): String = pathsByName.getOrPut(fileName) {
        filesByName[fileName]?.map { it.relativeTo(projectRoot).path }?.firstOrNull() ?: fileName
    }
}
