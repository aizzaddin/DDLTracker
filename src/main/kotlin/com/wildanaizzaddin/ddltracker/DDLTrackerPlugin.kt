package com.wildanaizzaddin.ddltracker

import com.intellij.database.DatabaseTopics
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.wildanaizzaddin.ddltracker.listener.DDLQueryListener

class DDLTrackerPlugin : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        project.messageBus
            .connect(project)
            .subscribe(DatabaseTopics.AUDIT_TOPIC, DDLQueryListener(project))
    }
}
