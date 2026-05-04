package com.wildanaizzaddin.ddltracker

import com.intellij.database.DataBus
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.wildanaizzaddin.ddltracker.listener.DDLQueryListener

class DDLTrackerPlugin : ProjectActivity {

    override suspend fun execute(project: Project) {
        DataBus.addRootAuditor(project, DDLQueryListener(project), project)
    }
}
