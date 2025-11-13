package com.netflix.nebula.archrules.gradle

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream

class ViolationsUtil {
    companion object {
        @JvmStatic
        fun readDetails(dataFile: File): List<RuleResult> {
            val list: MutableList<RuleResult> = mutableListOf()
            try {
                ObjectInputStream(FileInputStream(dataFile)).use { objectInputStream ->
                    val numObjects = objectInputStream.readInt()
                    repeat(numObjects) {
                        list.add(objectInputStream.readObject() as RuleResult)
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
            return list
        }
    }
}