package com.wildanaizzaddin.ddltracker.service

import com.wildanaizzaddin.ddltracker.model.DDLChange
import java.io.File
import java.time.format.DateTimeFormatter

object FileWriterService {
    private val FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val HEADER_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

    fun write(change: DDLChange, repoRoot: String): File {
        val dir = File(repoRoot, "migrations/${change.schema}")
        dir.mkdirs()
        val name = "${change.timestamp.format(FILE_TS)}__${change.actionType}_${change.objectName}.sql"
        val file = File(dir, name)
        file.writeText(buildContent(change))
        return file
    }

    private fun buildContent(change: DDLChange) = buildString {
        appendLine("-- DDL Change Tracker")
        appendLine("-- Timestamp  : ${change.timestamp.format(HEADER_TS)}")
        appendLine("-- User       : ${change.user}")
        appendLine("-- Datasource : ${change.datasource}")
        appendLine("-- Schema     : ${change.schema}")
        if (change.project.isNotBlank()) appendLine("-- Project    : ${change.project}")
        appendLine()
        append(change.sql)
    }
}
