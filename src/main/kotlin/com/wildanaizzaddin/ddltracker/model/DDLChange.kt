package com.wildanaizzaddin.ddltracker.model

import java.time.LocalDateTime

enum class CommitStatus { PENDING, COMMITTED, FAILED }

data class DDLChange(
    val sql: String,
    val timestamp: LocalDateTime,
    val user: String,
    val datasource: String,
    val schema: String,
    val objectName: String,
    val actionType: String,
    var commitStatus: CommitStatus = CommitStatus.PENDING
)
