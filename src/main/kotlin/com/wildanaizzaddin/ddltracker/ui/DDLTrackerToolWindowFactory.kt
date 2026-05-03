package com.wildanaizzaddin.ddltracker.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class DDLTrackerToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val window = DDLTrackerToolWindow(project)
        val content = ContentFactory.getInstance().createContent(window.component, "", false)
        toolWindow.contentManager.addContent(content)
    }
}
