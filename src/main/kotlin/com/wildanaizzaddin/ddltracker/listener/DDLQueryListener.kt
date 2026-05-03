package com.wildanaizzaddin.ddltracker.listener

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.wildanaizzaddin.ddltracker.model.CommitStatus
import com.wildanaizzaddin.ddltracker.service.DDLFilterService
import com.wildanaizzaddin.ddltracker.service.FileWriterService
import com.wildanaizzaddin.ddltracker.service.GitCommitService
import com.wildanaizzaddin.ddltracker.settings.DDLTrackerSettings
import com.wildanaizzaddin.ddltracker.ui.DDLTrackerToolWindow
import com.intellij.database.datagrid.DataAuditor
import com.intellij.database.datagrid.DataRequest

class DDLQueryListener(private val project: Project) : DataAuditor {

    private val LOG = logger<DDLQueryListener>()

    override fun afterStatement(context: DataRequest.Context) {
        val sql = context.getStatementContext()?.sql ?: context.query

        // [DEBUG] Remove after confirmed working
        notify("[DDL Tracker] New activity tracked: $sql", NotificationType.WARNING)

        if (!DDLFilterService.isDDL(sql)) return

        val settings = DDLTrackerSettings.getInstance()
        val datasource = context.request.owner.getDisplayName()
        val activeSchema = context.searchPath?.getDisplayName() ?: ""

        val tracked = settings.state.trackedDatasources
        if (tracked.isNotBlank()) {
            val trackedList = tracked.split(',').map { it.trim() }
            if (datasource !in trackedList) return
        }

        val excluded = settings.state.excludedSchemas.split(',').map { it.trim().uppercase() }
        val change = DDLFilterService.parse(sql, datasource, activeSchema) ?: return
        if (change.schema in excluded) return

        DDLTrackerToolWindow.addChange(project, change)

        if (!settings.state.autoCommit || settings.state.repoPath.isBlank()) return

        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                val file = FileWriterService.write(change, settings.state.repoPath)
                GitCommitService(settings).commitAndPush(change, file)
                    .onSuccess { message ->
                        change.commitStatus = CommitStatus.COMMITTED
                        DDLTrackerToolWindow.refresh(project)
                        notifySuccess(message, settings.state.activeBranch)
                    }
                    .onFailure { err ->
                        change.commitStatus = CommitStatus.FAILED
                        DDLTrackerToolWindow.refresh(project)
                        notifyPushFailure(err)
                    }
            }.onFailure { err ->
                LOG.error("[DDL Tracker] unexpected error processing statement", err)
            }
        }
    }

    private fun notify(msg: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("DDL Tracker")
            .createNotification(msg, type)
            .notify(project)
    }

    private fun notifySuccess(message: String, branch: String) =
        notify("[DDL Tracker] Committed to $branch\n$message", NotificationType.INFORMATION)

    private fun notifyPushFailure(err: Throwable) =
        notify("[DDL Tracker] Push failed — ${err.message}", NotificationType.WARNING)
}
