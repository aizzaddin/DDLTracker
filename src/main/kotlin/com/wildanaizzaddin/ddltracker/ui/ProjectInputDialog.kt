package com.wildanaizzaddin.ddltracker.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.wildanaizzaddin.ddltracker.model.DDLChange
import com.wildanaizzaddin.ddltracker.settings.DDLTrackerSettings
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel

class ProjectInputDialog(ideProject: Project, private val change: DDLChange) : DialogWrapper(ideProject) {

    private val projectCombo = ComboBox<String>().apply {
        isEditable = true
        preferredSize = Dimension(300, preferredSize.height)
    }

    init {
        title = "DDL Activity — Assign Project"
        setOKButtonText("Commit")
        setCancelButtonText("Skip")

        val recent = DDLTrackerSettings.getInstance().state.recentProjects
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }
        recent.forEach { projectCombo.addItem(it) }
        projectCombo.selectedItem = ""

        init()
    }

    override fun createCenterPanel(): JComponent {
        val info = JLabel("<html><b>${change.actionType.replace('_', ' ')}</b> on <b>${change.schema}.${change.objectName}</b></html>").apply {
            border = JBUI.Borders.emptyBottom(8)
        }
        return FormBuilder.createFormBuilder()
            .addComponent(info)
            .addLabeledComponent("Project:", projectCombo)
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
            .also { it.preferredSize = Dimension(400, 100) }
    }

    fun getProjectName(): String = projectCombo.editor.item.toString().trim().uppercase()
}
