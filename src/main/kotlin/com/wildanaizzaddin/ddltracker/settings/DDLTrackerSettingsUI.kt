package com.wildanaizzaddin.ddltracker.settings

import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.wildanaizzaddin.ddltracker.service.hostPortFromJdbcUrl
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

class DDLTrackerSettingsUI(private val project: Project) : Configurable {

    private val repoPathField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            "Select Git Repository Root", null, null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
    }
    private val remoteUrlField = JBTextField()
    private val branchField = JBTextField()
    private val autoCommitBox = JBCheckBox("Auto-commit on DDL")
    private val autoPushBox = JBCheckBox("Auto-push after commit")
    private val excludedSchemasField = JBTextField()

    // key (host:port) → JCheckBox
    private val datasourceCheckboxes = mutableMapOf<String, JCheckBox>()
    private val datasourceContainer = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val datasourceScrollPane = JBScrollPane(datasourceContainer).apply {
        preferredSize = Dimension(0, 130)
    }

    private var panel: JPanel? = null

    override fun getDisplayName() = "DDL Change Tracker"

    override fun createComponent(): JComponent {
        val refreshBtn = JButton("Refresh").apply {
            addActionListener { refreshDatasources() }
        }
        val datasourcePanel = JPanel(BorderLayout(0, 4)).apply {
            add(refreshBtn, BorderLayout.NORTH)
            add(datasourceScrollPane, BorderLayout.CENTER)
        }

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Git repository path:", repoPathField)
            .addLabeledComponent("Remote URL:", remoteUrlField)
            .addLabeledComponent("Active branch:", branchField)
            .addComponent(autoCommitBox)
            .addComponent(autoPushBox)
            .addLabeledComponent("Excluded schemas:", excludedSchemasField)
            .addSeparator()
            .addLabeledComponent("<html>Tracked datasources<br>(none = track all):</html>", datasourcePanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val s = settings()
        return repoPathField.text != s.repoPath ||
            remoteUrlField.text != s.remoteUrl ||
            branchField.text != s.activeBranch ||
            autoCommitBox.isSelected != s.autoCommit ||
            autoPushBox.isSelected != s.autoPush ||
            excludedSchemasField.text != s.excludedSchemas ||
            selectedDatasources() != s.trackedDatasources
    }

    override fun apply() {
        settings().apply {
            repoPath = repoPathField.text.trim()
            remoteUrl = remoteUrlField.text.trim()
            activeBranch = branchField.text.trim()
            autoCommit = autoCommitBox.isSelected
            autoPush = autoPushBox.isSelected
            excludedSchemas = excludedSchemasField.text.trim()
            trackedDatasources = selectedDatasources()
        }
    }

    override fun reset() {
        settings().let { s ->
            repoPathField.text = s.repoPath
            remoteUrlField.text = s.remoteUrl
            branchField.text = s.activeBranch
            autoCommitBox.isSelected = s.autoCommit
            autoPushBox.isSelected = s.autoPush
            excludedSchemasField.text = s.excludedSchemas
            refreshDatasources()
        }
    }

    private fun refreshDatasources() {
        val saved = settings().trackedDatasources
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        val items = runCatching {
            LocalDataSourceManager.getInstance(project).dataSources.map { ds ->
                val hp = hostPortFromJdbcUrl(ds.url ?: "")
                val label = if (hp != null) "${ds.name} — $hp" else ds.name
                ds.name to label
            }.sortedBy { it.second }
        }.getOrDefault(emptyList())

        datasourceContainer.removeAll()
        datasourceCheckboxes.clear()
        for ((key, label) in items) {
            val cb = JCheckBox(label, key in saved)
            datasourceCheckboxes[key] = cb
            datasourceContainer.add(cb)
        }
        datasourceContainer.revalidate()
        datasourceContainer.repaint()
    }

    private fun selectedDatasources(): String =
        datasourceCheckboxes.filter { (_, cb) -> cb.isSelected }.keys.joinToString(",")

    private fun settings() = DDLTrackerSettings.getInstance().state
}
