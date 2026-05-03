package com.wildanaizzaddin.ddltracker

import com.intellij.database.DataBus
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.wildanaizzaddin.ddltracker.listener.DDLQueryListener

class DDLTrackerPlugin : StartupActivity.DumbAware {

    override fun runActivity(project: Project) {
        DataBus.addRootAuditor(project, DDLQueryListener(project), project)

        // [DEBUG] Remove after confirmed working
        NotificationGroupManager.getInstance()
            .getNotificationGroup("DDL Tracker")
            .createNotification("DDL Tracker: startup OK — listening for DDL", NotificationType.INFORMATION)
            .notify(project)
    }
}
