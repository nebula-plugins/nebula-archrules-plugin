package com.netflix.nebula.archrules.gradle

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Helpers for dealing with [RuleResult]
 */
class ViolationsUtil {
    companion object {
        val log: Logger = LoggerFactory.getLogger(ViolationsUtil::class.java)

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
                log.warn("Archrules data read failed for {}", dataFile.absolutePath, e)
            } catch (e: ClassNotFoundException) {
                throw RuntimeException(e)
            }
            return list
        }

        @JvmStatic
        fun writeDetails(dataFile: File, violationList: List<RuleResult>) {
            ObjectOutputStream(FileOutputStream(dataFile)).use { out ->
                out.writeInt(violationList.size)
                violationList.forEach {
                    out.writeObject(it)
                }
            }
        }
    }
}
