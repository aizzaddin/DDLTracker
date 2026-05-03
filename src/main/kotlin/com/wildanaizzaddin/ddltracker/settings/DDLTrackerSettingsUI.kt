package com.wildanaizzaddin.ddltracker.settings

import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

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
    private val datasourceList = CheckBoxList<String>()

    private var panel: JPanel? = null

    override fun getDisplayName() = "DDL Change Tracker"

    override fun createComponent(): JComponent {
        val refreshBtn = JButton("Refresh").apply {
            addActionListener { refreshDatasources() }
        }
        val datasourcePanel = JPanel(BorderLayout(0, 4)).apply {
            add(refreshBtn, BorderLayout.NORTH)
            add(JBScrollPane(datasourceList).apply {
                preferredSize = Dimension(0, 130)
            }, BorderLayout.CENTER)
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
            LocalDataSourceManager.getInstance(project).dataSources.mapNotNull { ds ->
                val hp = extractHostPort(ds.url ?: return@mapNotNull null) ?: return@mapNotNull null
                hp to "${ds.name} — $hp"
            }.sortedBy { it.second }
        }.getOrDefault(emptyList())
        datasourceList.clear()
        for ((key, label) in items) {
            datasourceList.addItem(key, label, key in saved)
        }
    }

    private fun extractHostPort(jdbcUrl: String): String? = try {
        val uri = java.net.URI(jdbcUrl.removePrefix("jdbc:"))
        if (uri.host != null && uri.port != -1) "${uri.host}:${uri.port}"
        else uri.host
    } catch (_: Exception) { null }

    private fun selectedDatasources(): String {
        val selected = mutableListOf<String>()
        for (i in 0 until datasourceList.model.size) {
            if (datasourceList.isItemSelected(i)) {
                datasourceList.getItemAt(i)?.let { selected.add(it) }
            }
        }
        return selected.joinToString(",")
    }

    private fun settings() = DDLTrackerSettings.getInstance().state
}
