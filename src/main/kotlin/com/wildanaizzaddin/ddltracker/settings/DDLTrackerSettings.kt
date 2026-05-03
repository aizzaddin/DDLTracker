package com.wildanaizzaddin.ddltracker.settings

import com.intellij.openapi.components.*

@State(
    name = "DDLTrackerSettings",
    storages = [Storage("ddl-tracker.xml")]
)
@Service(Service.Level.APP)
class DDLTrackerSettings : PersistentStateComponent<DDLTrackerSettings.State> {

    data class State(
        var repoPath: String = "",
        var remoteUrl: String = "",
        var activeBranch: String = "main",
        var autoCommit: Boolean = true,
        var autoPush: Boolean = false,
        var trackAllDatasources: Boolean = true,
        var excludedSchemas: String = "SYS,SYSTEM,DBSNMP"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): DDLTrackerSettings = service()
    }
}
