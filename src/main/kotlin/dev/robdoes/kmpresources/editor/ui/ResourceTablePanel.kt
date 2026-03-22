package dev.robdoes.kmpresources.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.robdoes.kmpresources.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.service.ResourceScannerService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

data class ResourceStatus(val icon: Icon?, val tooltip: String?)

class ResourceTablePanel(private val scannerService: ResourceScannerService) : JPanel(BorderLayout()) {

    var onInlineStringEdited: ((key: String, isUntranslatable: Boolean, newValue: String) -> Unit)? = null
    var onInlinePluralEdited: ((key: String, isUntranslatable: Boolean, quantity: String, newValue: String) -> Unit)? = null
    var onInlineArrayEdited: ((key: String, isUntranslatable: Boolean, index: Int, newValue: String) -> Unit)? = null // NEU

    var onUntranslatableToggled: ((key: String, isUntranslatable: Boolean) -> Unit)? = null
    var onEditRequested: ((key: String) -> Unit)? = null
    var onDeleteRequested: ((key: String, type: String, isSubItem: Boolean) -> Unit)? = null
    var onUsageRequested: ((key: String) -> Unit)? = null

    private val validQuantities = listOf("zero", "one", "two", "few", "many", "other")
    private val tableModel: DefaultTableModel
    private val tableRowSorter: TableRowSorter<DefaultTableModel>
    private val table: JBTable

    init {
        val columnNames = arrayOf(
            KmpResourcesBundle.message("table.column.status"),
            KmpResourcesBundle.message("table.column.key"),
            KmpResourcesBundle.message("table.column.usage"),
            KmpResourcesBundle.message("table.column.delete"),
            KmpResourcesBundle.message("table.column.untranslatable"),
            KmpResourcesBundle.message("table.column.type"),
            KmpResourcesBundle.message("table.column.default.value")
        )

        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int) = when (columnIndex) {
                0 -> ResourceStatus::class.java
                2, 3 -> Icon::class.java
                4 -> Boolean::class.javaObjectType
                else -> String::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                val type = getValueAt(row, 5) as? String ?: return false
                if (column == 4) return type == "string" || type == "plurals" || type == "string-array"
                if (column == 6) return type == "string" || type in validQuantities || type.startsWith("item[")
                return false
            }

            override fun setValueAt(aValue: Any?, row: Int, column: Int) {
                val oldValue = getValueAt(row, column)
                super.setValueAt(aValue, row, column)

                if (column == 6 && oldValue != aValue) {
                    val newValue = aValue as? String ?: ""
                    val type = getValueAt(row, 5) as? String ?: return
                    val parentKey = getParentKeyNameForRow(row) ?: return
                    val isUn = getValueAt(row, 4) as? Boolean ?: false

                    if (type == "string") {
                        onInlineStringEdited?.invoke(parentKey, isUn, newValue)
                    } else if (type in validQuantities) {
                        onInlinePluralEdited?.invoke(parentKey, isUn, type, newValue)
                        if (newValue.isNotBlank() && getValueAt(row, 3) == null) setValueAt(AllIcons.General.Remove, row, 3)
                    } else if (type.startsWith("item[")) {
                        // Array Inline Editing
                        val indexStr = type.substringAfter("[").substringBefore("]")
                        val index = if (indexStr == "+") -1 else indexStr.toIntOrNull() ?: -1
                        onInlineArrayEdited?.invoke(parentKey, isUn, index, newValue)
                    }
                }

                if (column == 4 && oldValue != aValue) {
                    onUntranslatableToggled?.invoke(getValueAt(row, 1) as String, aValue as? Boolean ?: false)
                }
            }
        }

        table = JBTable(tableModel)
        tableRowSorter = TableRowSorter(tableModel)
        table.rowSorter = tableRowSorter

        table.columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(t: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int): Component {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column)
                text = ""; horizontalAlignment = SwingConstants.CENTER
                if (value is ResourceStatus) { icon = value.icon; toolTipText = if (icon == AllIcons.General.Error) "${value.tooltip} (Click to remove)" else value.tooltip }
                else { icon = null; toolTipText = null }
                return this
            }
        }

        table.columnModel.getColumn(0).maxWidth = 45
        table.columnModel.getColumn(1).preferredWidth = 350; table.columnModel.getColumn(1).minWidth = 200
        table.columnModel.getColumn(2).maxWidth = 50; table.columnModel.getColumn(3).maxWidth = 50
        table.columnModel.getColumn(4).maxWidth = 100

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val viewRow = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (viewRow < 0) return
                val modelRow = table.convertRowIndexToModel(viewRow)

                when (col) {
                    0 -> if ((tableModel.getValueAt(modelRow, 0) as? ResourceStatus)?.icon == AllIcons.General.Error) triggerDelete(modelRow)
                    1 -> getParentKeyNameForRow(modelRow)?.let { onEditRequested?.invoke(it) }
                    2 -> {
                        val keyName = tableModel.getValueAt(modelRow, 1) as String
                        if (keyName.isNotBlank()) onUsageRequested?.invoke(keyName)
                    }
                    3 -> if (tableModel.getValueAt(modelRow, 3) != null) triggerDelete(modelRow)
                }
            }
        })

        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun triggerDelete(modelRow: Int) {
        val type = tableModel.getValueAt(modelRow, 5) as? String ?: return
        val parentKey = getParentKeyNameForRow(modelRow) ?: return
        val isSubItem = type in validQuantities || type.startsWith("item[")
        onDeleteRequested?.invoke(parentKey, type, isSubItem)
    }

    fun updateData(resources: List<XmlResource>) {
        tableModel.rowCount = 0
        val loadingStatus = ResourceStatus(AllIcons.Actions.Refresh, KmpResourcesBundle.message("status.tooltip.analyzing"))

        resources.forEach { res ->
            when (res) {
                is StringResource -> tableModel.addRow(arrayOf(loadingStatus, res.key, AllIcons.Actions.Search, AllIcons.General.Remove, res.isUntranslatable, "string", res.value))

                is PluralsResource -> {
                    tableModel.addRow(arrayOf(loadingStatus, res.key, AllIcons.Actions.Search, AllIcons.General.Remove, res.isUntranslatable, "plurals", ""))
                    validQuantities.forEach { q ->
                        val valueText = res.items[q] ?: ""
                        val deleteIcon = if (res.items.containsKey(q)) AllIcons.General.Remove else null
                        tableModel.addRow(arrayOf(null, "", null, deleteIcon, null, q, valueText))
                    }
                }

                is StringArrayResource -> {
                    // ARRAY Render Logic
                    tableModel.addRow(arrayOf(loadingStatus, res.key, AllIcons.Actions.Search, AllIcons.General.Remove, res.isUntranslatable, "string-array", ""))
                    res.items.forEachIndexed { index, itemValue ->
                        tableModel.addRow(arrayOf(null, "", null, AllIcons.General.Remove, null, "item[$index]", itemValue))
                    }
                    // Leere Zeile zum direkten Inline-Hinzufügen
                    tableModel.addRow(arrayOf(null, "", null, null, null, "item[+]", ""))
                }
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            for (i in 0 until tableModel.rowCount) {
                val keyName = tableModel.getValueAt(i, 1) as? String
                if (keyName.isNullOrBlank()) continue

                val isUsed = scannerService.isResourceUsed(keyName)
                ApplicationManager.getApplication().invokeLater {
                    if (i < tableModel.rowCount && tableModel.getValueAt(i, 1) == keyName) {
                        val newStatus = if (isUsed) ResourceStatus(null, KmpResourcesBundle.message("status.tooltip.ok"))
                        else ResourceStatus(AllIcons.General.Error, KmpResourcesBundle.message("status.tooltip.unused"))
                        tableModel.setValueAt(newStatus, i, 0)
                    }
                }
            }
        }
    }

    fun applyFilter(filter: String) {
        tableRowSorter.rowFilter = object : RowFilter<DefaultTableModel, Int>() {
            override fun include(entry: Entry<out DefaultTableModel, out Int>): Boolean {
                if (filter == "ALL") return true
                val type = entry.getStringValue(5)
                return when (filter) {
                    "STRINGS" -> type == "string"
                    "PLURALS" -> type == "plurals" || type in validQuantities
                    "ARRAYS" -> type == "string-array" || type.startsWith("item[")
                    else -> true
                }
            }
        }
    }

    fun getSelectedRowKey(): String? {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return null
        return getParentKeyNameForRow(table.convertRowIndexToModel(selectedRow))
    }

    fun hasSelection() = table.selectedRow >= 0

    fun triggerDeleteForSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) triggerDelete(table.convertRowIndexToModel(selectedRow))
    }

    fun scrollToKey(key: String) {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getValueAt(i, 1) == key) {
                table.setRowSelectionInterval(i, i)
                table.scrollRectToVisible(table.getCellRect(i, 0, true))
                break
            }
        }
    }

    private fun getParentKeyNameForRow(startRow: Int): String? {
        var currentRow = startRow
        while (currentRow >= 0) {
            val keyName = tableModel.getValueAt(currentRow, 1) as String
            if (keyName.isNotBlank()) return keyName
            currentRow--
        }
        return null
    }
}