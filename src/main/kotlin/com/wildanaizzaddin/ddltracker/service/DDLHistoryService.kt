package com.wildanaizzaddin.ddltracker.service

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.wildanaizzaddin.ddltracker.model.CommitStatus
import com.wildanaizzaddin.ddltracker.model.DDLChange
import java.time.LocalDateTime

@State(name = "DDLTrackerHistory", storages = [Storage("ddl-tracker-history.xml")])
@Service(Service.Level.PROJECT)
class DDLHistoryService : PersistentStateComponent<DDLHistoryService.State> {

    data class Entry(
        var sql: String = "",
        var timestamp: String = "",
        var user: String = "",
        var datasource: String = "",
        var schema: String = "",
        var objectName: String = "",
        var actionType: String = "",
        var commitStatus: String = "PENDING"
    )

    data class State(var entries: MutableList<Entry> = mutableListOf())

    private var myState = State()
    override fun getState(): State = myState
    override fun loadState(state: State) { myState = state }

    fun add(change: DDLChange) {
        myState.entries.add(0, change.toEntry())
        if (myState.entries.size > MAX_ENTRIES) {
            myState.entries = myState.entries.take(MAX_ENTRIES).toMutableList()
        }
    }

    fun updateStatus(change: DDLChange) {
        val ts = change.timestamp.toString()
        myState.entries.find { it.timestamp == ts }?.commitStatus = change.commitStatus.name
    }

    fun getAll(): List<DDLChange> = myState.entries.mapNotNull { it.toChange() }

    private fun DDLChange.toEntry() =
        Entry(sql, timestamp.toString(), user, datasource, schema, objectName, actionType, commitStatus.name)

    private fun Entry.toChange() = runCatching {
        DDLChange(sql, LocalDateTime.parse(timestamp), user, datasource, schema, objectName, actionType,
            CommitStatus.valueOf(commitStatus))
    }.getOrNull()

    companion object {
        const val MAX_ENTRIES = 500
        fun getInstance(project: Project): DDLHistoryService = project.service()
    }
}
