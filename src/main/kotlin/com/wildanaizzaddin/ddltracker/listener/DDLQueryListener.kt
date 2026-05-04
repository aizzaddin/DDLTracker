package com.wildanaizzaddin.ddltracker.listener

import com.intellij.database.connection.throwable.info.ErrorInfo
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.wildanaizzaddin.ddltracker.model.CommitStatus
import com.wildanaizzaddin.ddltracker.service.DDLFilterService
import com.wildanaizzaddin.ddltracker.service.DDLHistoryService
import com.wildanaizzaddin.ddltracker.service.FileWriterService
import com.wildanaizzaddin.ddltracker.service.GitCommitService
import com.wildanaizzaddin.ddltracker.settings.DDLTrackerSettings
import com.wildanaizzaddin.ddltracker.ui.DDLTrackerToolWindow
import com.wildanaizzaddin.ddltracker.ui.ProjectInputDialog
import com.intellij.database.datagrid.DataAuditor
import com.intellij.database.datagrid.DataRequest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class DDLQueryListener(private val project: Project) : DataAuditor {

    private val LOG = logger<DDLQueryListener>()
    private val failedContexts: MutableSet<DataRequest.Context> =
        Collections.newSetFromMap(ConcurrentHashMap())

    override fun error(context: DataRequest.Context, errorInfo: ErrorInfo) {
        failedContexts.add(context)
    }

    override fun afterStatement(context: DataRequest.Context) {
        if (failedContexts.remove(context)) return
        val sql = context.getStatementContext()?.sql ?: context.query

        if (!DDLFilterService.isDDL(sql)) return

        val settings = DDLTrackerSettings.getInstance()
        val datasource = resolveDatasourceName(context)
        val activeSchema = context.searchPath?.getDisplayName() ?: ""

        // [DEBUG] Remove after confirmed working
        notify("[DDL Tracker] DDL detected | datasource: \"$datasource\" | schema: \"$activeSchema\"", NotificationType.WARNING)

        val tracked = settings.state.trackedDatasources
        if (tracked.isNotBlank()) {
            val trackedList = tracked.split(',').map { it.trim().lowercase() }
            if (datasource.lowercase() !in trackedList) {
                notify("[DDL Tracker] Skipped — \"$datasource\" not in tracked list: $tracked", NotificationType.WARNING)
                return
            }
        }

        val excluded = settings.state.excludedSchemas.split(',').map { it.trim().uppercase() }
        val change = DDLFilterService.parse(sql, datasource, activeSchema) ?: return
        if (change.schema in excluded) return

        DDLTrackerToolWindow.addChange(project, change)

        if (!settings.state.autoCommit) {
            notify("[DDL Tracker] Captured (auto-commit is off)", NotificationType.INFORMATION)
            return
        }
        if (settings.state.repoPath.isBlank()) {
            notify("[DDL Tracker] Captured — set a repo path in settings to enable auto-commit", NotificationType.WARNING)
            return
        }

        val repoPath = settings.state.repoPath
        val activeBranch = settings.state.activeBranch
        ApplicationManager.getApplication().invokeLater {
            val dialog = ProjectInputDialog(project, change)
            if (!dialog.showAndGet()) return@invokeLater

            val projectName = dialog.getProjectName()
            change.project = projectName
            if (projectName.isNotBlank()) saveRecentProject(projectName, settings)

            ApplicationManager.getApplication().executeOnPooledThread {
                runCatching {
                    val file = FileWriterService.write(change, repoPath)
                    GitCommitService(settings).commitAndPush(change, file)
                        .onSuccess { message ->
                            change.commitStatus = CommitStatus.COMMITTED
                            runCatching { DDLHistoryService.getInstance(project).updateStatus(change) }
                            DDLTrackerToolWindow.refresh(project)
                            notifySuccess(message, activeBranch)
                        }
                        .onFailure { err ->
                            change.commitStatus = CommitStatus.FAILED
                            runCatching { DDLHistoryService.getInstance(project).updateStatus(change) }
                            DDLTrackerToolWindow.refresh(project)
                            notifyPushFailure(err)
                        }
                }.onFailure { err ->
                    LOG.error("[DDL Tracker] unexpected error processing statement", err)
                }
            }
        }
    }

    private fun resolveDatasourceName(context: DataRequest.Context): String {
        val owner = context.request.owner

        fun Any.call0(name: String): Any? =
            runCatching { javaClass.getMethod(name).invoke(this) }.getOrNull()
                ?: runCatching {
                    javaClass.getDeclaredMethod(name).also { it.isAccessible = true }.invoke(this)
                }.getOrNull()

        fun Any.dsName(): String? =
            (call0("getName") as? String)?.takeIf { it.isNotBlank() }

        // Path 1: getLocalDataSource().getName()
        owner.call0("getLocalDataSource")?.dsName()?.let { return it }

        // Path 2: getConnectionPoint().getDataSource().getName()
        owner.call0("getConnectionPoint")
            ?.call0("getDataSource")?.dsName()?.let { return it }

        // Path 3: getSession().getConnectionPoint().getDataSource().getName()
        owner.call0("getSession")
            ?.call0("getConnectionPoint")
            ?.call0("getDataSource")?.dsName()?.let { return it }

        // Path 4: getDataSource().getName()
        owner.call0("getDataSource")?.dsName()?.let { return it }

        // Path 5: getClient().getConnectionPoint().getDataSource().getName()
        owner.call0("getClient")
            ?.call0("getConnectionPoint")
            ?.call0("getDataSource")?.dsName()?.let { return it }

        val pub = owner.javaClass.methods
            .filter { it.parameterCount == 0 }
            .map { "${it.name}→${it.returnType.simpleName}" }
            .sorted()
        val decl = owner.javaClass.declaredMethods
            .filter { it.parameterCount == 0 }
            .map { "${it.name}→${it.returnType.simpleName}" }
            .sorted()
        LOG.warn("[DDL Tracker] resolveDatasourceName fallback\n  class : ${owner.javaClass.name}\n  public: $pub\n  decl  : $decl")
        return owner.getDisplayName()
    }

    private fun saveRecentProject(name: String, settings: DDLTrackerSettings) {
        val list = settings.state.recentProjects
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        list.remove(name)
        list.add(0, name)
        settings.state.recentProjects = list.take(10).joinToString(",")
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
