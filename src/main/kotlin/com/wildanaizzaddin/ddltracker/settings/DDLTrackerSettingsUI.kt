package com.wildanaizzaddin.ddltracker.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class DDLTrackerSettingsUI : Configurable {

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
    private val trackAllBox = JBCheckBox("Track all datasources")
    private val excludedSchemasField = JBTextField()

    private var panel: JPanel? = null

    override fun getDisplayName() = "DDL Change Tracker"

    override fun createComponent(): JComponent {
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Git repository path:", repoPathField)
            .addLabeledComponent("Remote URL:", remoteUrlField)
            .addLabeledComponent("Active branch:", branchField)
            .addComponent(autoCommitBox)
            .addComponent(autoPushBox)
            .addComponent(trackAllBox)
            .addLabeledComponent("Excluded schemas:", excludedSchemasField)
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
            trackAllBox.isSelected != s.trackAllDatasources ||
            excludedSchemasField.text != s.excludedSchemas
    }

    override fun apply() {
        settings().apply {
            repoPath = repoPathField.text.trim()
            remoteUrl = remoteUrlField.text.trim()
            activeBranch = branchField.text.trim()
            autoCommit = autoCommitBox.isSelected
            autoPush = autoPushBox.isSelected
            trackAllDatasources = trackAllBox.isSelected
            excludedSchemas = excludedSchemasField.text.trim()
        }
    }

    override fun reset() {
        settings().let { s ->
            repoPathField.text = s.repoPath
            remoteUrlField.text = s.remoteUrl
            branchField.text = s.activeBranch
            autoCommitBox.isSelected = s.autoCommit
            autoPushBox.isSelected = s.autoPush
            trackAllBox.isSelected = s.trackAllDatasources
            excludedSchemasField.text = s.excludedSchemas
        }
    }

    private fun settings() = DDLTrackerSettings.getInstance().state
}
