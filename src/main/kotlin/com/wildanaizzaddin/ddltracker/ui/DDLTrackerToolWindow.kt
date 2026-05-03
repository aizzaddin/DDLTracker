package com.wildanaizzaddin.ddltracker.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.wildanaizzaddin.ddltracker.model.DDLChange
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel

class DDLTrackerToolWindow(private val project: Project) {

    private val changes = mutableListOf<DDLChange>()
    private val tableModel = ChangeTableModel()
    private val table = JBTable(tableModel)
    private val sqlPreview = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        font = JBUI.Fonts.create("JetBrains Mono", 12)
    }

    val component: JComponent = buildPanel()

    init {
        table.selectionModel.addListSelectionListener {
            val row = table.selectedRow.takeIf { it >= 0 } ?: return@addListSelectionListener
            sqlPreview.text = changes.getOrNull(row)?.sql ?: ""
            sqlPreview.caretPosition = 0
        }
        INSTANCE_MAP[project] = this
        // Flush any changes that arrived before the window was opened
        val pending = PENDING[project]
        if (!pending.isNullOrEmpty()) {
            changes.addAll(pending)
            tableModel.fireTableDataChanged()
            PENDING.remove(project)
        }
    }

    private fun buildPanel(): JComponent {
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = JBScrollPane(table)
            bottomComponent = JBScrollPane(sqlPreview)
            resizeWeight = 0.7
        }
        return JPanel(BorderLayout()).apply {
            add(splitPane, BorderLayout.CENTER)
        }
    }

    fun addChange(change: DDLChange) {
        changes.add(0, change)
        tableModel.fireTableDataChanged()
    }

    fun refresh() {
        tableModel.fireTableDataChanged()
    }

    private inner class ChangeTableModel : AbstractTableModel() {
        private val columns = arrayOf("Timestamp", "User", "Datasource", "Schema", "Action Type", "Object", "Status")

        override fun getColumnCount() = columns.size
        override fun getRowCount() = changes.size
        override fun getColumnName(col: Int) = columns[col]

        override fun getValueAt(row: Int, col: Int): Any {
            val c = changes[row]
            return when (col) {
                0 -> c.timestamp.toString()
                1 -> c.user
                2 -> c.datasource
                3 -> c.schema
                4 -> c.actionType
                5 -> c.objectName
                6 -> c.commitStatus.name
                else -> ""
            }
        }
    }

    companion object {
        private val INSTANCE_MAP = mutableMapOf<Project, DDLTrackerToolWindow>()
        private val PENDING = mutableMapOf<Project, MutableList<DDLChange>>()

        fun addChange(project: Project, change: DDLChange) {
            ApplicationManager.getApplication().invokeLater {
                val instance = INSTANCE_MAP[project]
                if (instance != null) {
                    instance.addChange(change)
                } else {
                    PENDING.getOrPut(project) { mutableListOf() }.add(0, change)
                }
            }
        }

        fun refresh(project: Project) {
            ApplicationManager.getApplication().invokeLater {
                INSTANCE_MAP[project]?.refresh()
            }
        }
    }
}
