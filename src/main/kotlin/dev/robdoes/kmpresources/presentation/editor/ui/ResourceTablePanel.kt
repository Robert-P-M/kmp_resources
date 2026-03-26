package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.service.ResourceScannerService
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableRowSorter

data class ResourceStatus(val icon: Icon?, val tooltip: String?)

enum class ResourceColumn(val index: Int, val titleKey: String) {
    STATUS(0, "ui.table.column.status"),
    KEY(1, "ui.table.column.key"),
    USAGE(2, "ui.table.column.usage"),
    DELETE(3, "ui.table.column.delete"),
    UNTRANSLATABLE(4, "ui.table.column.untranslatable"),
    TYPE(5, "ui.table.column.type"),
    DEFAULT_VALUE(6, "ui.table.column.default.value");

    companion object {
        fun fromIndex(index: Int) = entries.find { it.index == index }
    }
}

class ResourceTablePanel(private val project: Project, private val scannerService: ResourceScannerService) :
    JPanel(BorderLayout()) {

    var onInlineStringEdited: ((key: String, isUntranslatable: Boolean, newValue: String) -> Unit)? = null
    var onInlinePluralEdited: ((key: String, isUntranslatable: Boolean, quantity: String, newValue: String) -> Unit)? =
        null
    var onInlineArrayEdited: ((key: String, isUntranslatable: Boolean, index: Int, newValue: String) -> Unit)? = null

    var onUntranslatableToggled: ((key: String, isUntranslatable: Boolean) -> Unit)? = null
    var onEditRequested: ((key: String) -> Unit)? = null
    var onDeleteRequested: ((key: String, type: String, isSubItem: Boolean) -> Unit)? = null
    var onUsageRequested: ((key: String) -> Unit)? = null

    private val validQuantities = listOf("zero", "one", "two", "few", "many", "other")
    private val tableModel: DefaultTableModel
    private val tableRowSorter: TableRowSorter<DefaultTableModel>
    private val table: JBTable

    init {
        val columnNames = ResourceColumn.entries.map { KmpResourcesBundle.message(it.titleKey) }.toTypedArray()

        tableModel = object : DefaultTableModel(columnNames, 0) {
            override fun getColumnClass(columnIndex: Int) = when (ResourceColumn.fromIndex(columnIndex)) {
                ResourceColumn.STATUS -> ResourceStatus::class.java
                ResourceColumn.USAGE, ResourceColumn.DELETE -> Icon::class.java
                ResourceColumn.UNTRANSLATABLE -> Boolean::class.javaObjectType
                else -> String::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                val type = getValueAt(row, ResourceColumn.TYPE.index) as? String ?: return false
                return when (ResourceColumn.fromIndex(column)) {
                    ResourceColumn.UNTRANSLATABLE -> type in listOf("string", "plurals", "string-array")
                    ResourceColumn.DEFAULT_VALUE -> type == "string" || type in validQuantities || type.startsWith("item[")
                    else -> false
                }
            }

            override fun setValueAt(aValue: Any?, row: Int, column: Int) {
                val oldValue = getValueAt(row, column)
                super.setValueAt(aValue, row, column)

                if (oldValue == aValue) return
                val type = getValueAt(row, ResourceColumn.TYPE.index) as? String ?: return
                val parentKey = getParentKeyNameForRow(row) ?: return
                val isUn = getValueAt(row, ResourceColumn.UNTRANSLATABLE.index) as? Boolean ?: false

                when (ResourceColumn.fromIndex(column)) {
                    ResourceColumn.DEFAULT_VALUE -> {
                        val newValue = aValue as? String ?: ""
                        when {
                            type == "string" -> onInlineStringEdited?.invoke(parentKey, isUn, newValue)
                            type in validQuantities -> {
                                onInlinePluralEdited?.invoke(parentKey, isUn, type, newValue)
                                if (newValue.isNotBlank() && getValueAt(row, ResourceColumn.DELETE.index) == null) {
                                    setValueAt(AllIcons.General.Remove, row, ResourceColumn.DELETE.index)
                                }
                            }

                            type.startsWith("item[") -> {
                                val indexStr = type.substringAfter("[").substringBefore("]")
                                val index = if (indexStr == "+") -1 else indexStr.toIntOrNull() ?: -1
                                onInlineArrayEdited?.invoke(parentKey, isUn, index, newValue)
                            }
                        }
                    }

                    ResourceColumn.UNTRANSLATABLE -> {
                        onUntranslatableToggled?.invoke(parentKey, aValue as? Boolean ?: false)
                    }

                    else -> Unit
                }
            }
        }

        table = JBTable(tableModel).apply { autoResizeMode = JTable.AUTO_RESIZE_OFF }
        tableRowSorter = TableRowSorter(tableModel)
        table.rowSorter = tableRowSorter

        setupColumns()
        setupListeners()

        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    private fun setupColumns() {
        table.columnModel.getColumn(ResourceColumn.STATUS.index).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
            ): Component {
                return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column).apply {
                    (this as? JLabel)?.apply {
                        text = ""
                        horizontalAlignment = SwingConstants.CENTER
                        val status = value as? ResourceStatus
                        icon = status?.icon
                        toolTipText =
                            if (icon == AllIcons.General.Error) "${status?.tooltip} (Click to remove)" else status?.tooltip
                    }
                }
            }
        }

        ResourceColumn.entries.forEach { colEnum ->
            val col = table.columnModel.getColumn(colEnum.index)
            when (colEnum) {
                ResourceColumn.STATUS -> col.apply { preferredWidth = 45; maxWidth = 45; minWidth = 45 }
                ResourceColumn.KEY -> col.apply { preferredWidth = 300; minWidth = 150 }
                ResourceColumn.USAGE, ResourceColumn.DELETE -> col.apply {
                    preferredWidth = 50; maxWidth = 50; minWidth = 50
                }

                ResourceColumn.UNTRANSLATABLE -> col.apply { preferredWidth = 110; maxWidth = 110; minWidth = 110 }
                ResourceColumn.TYPE -> col.apply { preferredWidth = 100; minWidth = 80 }
                ResourceColumn.DEFAULT_VALUE -> col.apply { preferredWidth = 400; minWidth = 200 }
            }
        }
    }

    private fun setupListeners() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val viewRow = table.rowAtPoint(e.point)
                val colIndex = table.columnAtPoint(e.point)
                if (viewRow < 0) return
                val modelRow = table.convertRowIndexToModel(viewRow)

                when (ResourceColumn.fromIndex(colIndex)) {
                    ResourceColumn.STATUS -> {
                        if ((tableModel.getValueAt(
                                modelRow,
                                ResourceColumn.STATUS.index
                            ) as? ResourceStatus)?.icon == AllIcons.General.Error
                        ) {
                            triggerDelete(modelRow)
                        }
                    }

                    ResourceColumn.KEY -> getParentKeyNameForRow(modelRow)?.let { onEditRequested?.invoke(it) }
                    ResourceColumn.USAGE -> {
                        val keyName = tableModel.getValueAt(modelRow, ResourceColumn.KEY.index) as String
                        if (keyName.isNotBlank()) onUsageRequested?.invoke(keyName)
                    }

                    ResourceColumn.DELETE -> {
                        if (tableModel.getValueAt(
                                modelRow,
                                ResourceColumn.DELETE.index
                            ) != null
                        ) triggerDelete(modelRow)
                    }

                    else -> Unit
                }
            }
        })
    }

    private fun triggerDelete(modelRow: Int) {
        val type = tableModel.getValueAt(modelRow, ResourceColumn.TYPE.index) as? String ?: return
        val parentKey = getParentKeyNameForRow(modelRow) ?: return
        val isSubItem = type in validQuantities || type.startsWith("item[")
        onDeleteRequested?.invoke(parentKey, type, isSubItem)
    }

    fun updateData(resources: List<XmlResource>) {
        val loadingStatus =
            ResourceStatus(AllIcons.Actions.Refresh, KmpResourcesBundle.message("ui.table.status.tooltip.analyzing"))

        val newRows = mutableListOf<Array<Any?>>()

        resources.forEach { res ->
            when (res) {
                is StringResource -> newRows.add(
                    arrayOf(
                        loadingStatus,
                        res.key,
                        AllIcons.Actions.Search,
                        AllIcons.General.Remove,
                        res.isUntranslatable,
                        "string",
                        res.value
                    )
                )

                is PluralsResource -> {
                    newRows.add(
                        arrayOf(
                            loadingStatus,
                            res.key,
                            AllIcons.Actions.Search,
                            AllIcons.General.Remove,
                            res.isUntranslatable,
                            "plurals",
                            ""
                        )
                    )
                    validQuantities.forEach { q ->
                        val valueText = res.items[q] ?: ""
                        val deleteIcon = if (res.items.containsKey(q)) AllIcons.General.Remove else null
                        newRows.add(arrayOf(null, "", null, deleteIcon, null, q, valueText))
                    }
                }

                is StringArrayResource -> {
                    newRows.add(
                        arrayOf(
                            loadingStatus,
                            res.key,
                            AllIcons.Actions.Search,
                            AllIcons.General.Remove,
                            res.isUntranslatable,
                            "string-array",
                            ""
                        )
                    )
                    res.items.forEachIndexed { index, itemValue ->
                        newRows.add(
                            arrayOf(null, "", null, AllIcons.General.Remove, null, "item[$index]", itemValue)
                        )
                    }
                    newRows.add(arrayOf(null, "", null, null, null, "item[+]", ""))
                }
            }
        }

        val columnNames = ResourceColumn.entries.map { KmpResourcesBundle.message(it.titleKey) }.toTypedArray()
        tableModel.setDataVector(newRows.toTypedArray(), columnNames)
        setupColumns()

        val keysToEvaluate = newRows.mapIndexedNotNull { row, rowData ->
            val key = rowData[ResourceColumn.KEY.index] as? String
            if (key.isNullOrBlank()) null else row to key
        }

        project.service<KmpProjectScopeService>().coroutineScope.launch {
            DumbService.getInstance(project).waitForSmartMode()

            val evaluatedResults = keysToEvaluate.map { (row, key) ->
                row to (key to scannerService.isResourceUsed(key))
            }

            withContext(Dispatchers.EDT) {
                evaluatedResults.forEach { (row, pair) ->
                    val (key, isUsed) = pair
                    if (row < tableModel.rowCount && tableModel.getValueAt(row, ResourceColumn.KEY.index) == key) {
                        val newStatus = if (isUsed) {
                            ResourceStatus(
                                AllIcons.General.InspectionsOK,
                                KmpResourcesBundle.message("ui.table.status.tooltip.ok")
                            )
                        } else {
                            ResourceStatus(
                                AllIcons.General.Error,
                                KmpResourcesBundle.message("ui.table.status.tooltip.unused")
                            )
                        }
                        tableModel.setValueAt(newStatus, row, ResourceColumn.STATUS.index)
                    }
                }
            }
        }
    }

    fun applyFilter(filter: String) {
        tableRowSorter.rowFilter = object : RowFilter<DefaultTableModel, Int>() {
            override fun include(entry: Entry<out DefaultTableModel, out Int>): Boolean {
                if (filter == "ALL") return true
                val type = entry.getStringValue(ResourceColumn.TYPE.index)
                return when (filter) {
                    "STRINGS" -> type == "string"
                    "PLURALS" -> type == "plurals" || type in validQuantities
                    "ARRAYS" -> type == "string-array" || type.startsWith("item[")
                    else -> true
                }
            }
        }
    }

    fun hasSelection() = table.selectedRow >= 0

    fun triggerDeleteForSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) triggerDelete(table.convertRowIndexToModel(selectedRow))
    }

    fun scrollToKey(key: String) {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.EDT) {
            table.clearSelection()

            for (i in 0 until tableModel.rowCount) {
                if (tableModel.getValueAt(i, ResourceColumn.KEY.index) == key) {
                    val viewRow = table.convertRowIndexToView(i)

                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow)
                        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true))
                        IdeFocusManager.findInstance().requestFocus(table, true)
                    }
                    break
                }
            }
        }
    }

    private fun getParentKeyNameForRow(startRow: Int): String? {
        var currentRow = startRow
        while (currentRow >= 0) {
            val keyName = tableModel.getValueAt(currentRow, ResourceColumn.KEY.index) as String
            if (keyName.isNotBlank()) return keyName
            currentRow--
        }
        return null
    }
}