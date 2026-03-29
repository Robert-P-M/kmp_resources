package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.robdoes.kmpresources.core.application.service.LocaleDetectionService
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.awaitSmartMode
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.presentation.editor.controller.ResourceTablePaneController
import dev.robdoes.kmpresources.presentation.editor.model.ResourceColumn
import dev.robdoes.kmpresources.presentation.editor.model.ResourceStatus
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


class ResourceTablePanel(private val project: Project, private val scannerService: ResourceUsageService) :
    JPanel(BorderLayout()) {

    var onInlineStringEdited: ((key: String, localeTag: String?, isUn: Boolean, newValue: String) -> Unit)? = null
    var onInlinePluralEdited: ((key: String, localeTag: String?, isUn: Boolean, qty: String, newValue: String) -> Unit)? =
        null
    var onInlineArrayEdited: ((key: String, localeTag: String?, isUn: Boolean, idx: Int, newValue: String) -> Unit)? =
        null

    var onUntranslatableToggled: ((key: String, isUntranslatable: Boolean) -> Unit)? = null
    var onEditRequested: ((key: String) -> Unit)? = null
    var onDeleteRequested: ((key: String, type: String, isSubItem: Boolean) -> Unit)? = null
    var onUsageRequested: ((key: String) -> Unit)? = null

    private val validQuantities = ResourceType.Plural.supportedQuantities
    private val tableModel: DefaultTableModel
    private val tableRowSorter: TableRowSorter<DefaultTableModel>
    private val table: JBTable

    private var dynamicLocaleColumns = mutableMapOf<Int, String>()
    val controller = ResourceTablePaneController(
        project = project,
        scannerService = scannerService,
    )

    init {
        val baseColumns = ResourceColumn.entries.map { KmpResourcesBundle.message(it.titleKey) }.toTypedArray()

        tableModel = object : DefaultTableModel(baseColumns, 0) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                val staticCol = ResourceColumn.fromIndex(columnIndex)
                return when (staticCol) {
                    ResourceColumn.STATUS -> ResourceStatus::class.java
                    ResourceColumn.USAGE, ResourceColumn.DELETE -> Icon::class.java
                    ResourceColumn.UNTRANSLATABLE -> Boolean::class.javaObjectType
                    else -> String::class.java
                }
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                val type = getValueAt(row, ResourceColumn.TYPE.index) as? String ?: return false
                val staticCol = ResourceColumn.fromIndex(column)

                val isValueColumn =
                    staticCol == ResourceColumn.DEFAULT_VALUE || dynamicLocaleColumns.containsKey(column)

                return when {
                    staticCol == ResourceColumn.UNTRANSLATABLE -> type in listOf("string", "plurals", "string-array")
                    isValueColumn -> type == "string" || type in validQuantities || type.startsWith("item[")
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
                val localeTag = dynamicLocaleColumns[column]

                val staticCol = ResourceColumn.fromIndex(column)
                if (staticCol == ResourceColumn.DEFAULT_VALUE || dynamicLocaleColumns.containsKey(column)) {
                    val newValue = aValue as? String ?: ""
                    when {
                        type == "string" -> onInlineStringEdited?.invoke(parentKey, localeTag, isUn, newValue)
                        type in validQuantities -> onInlinePluralEdited?.invoke(
                            parentKey,
                            localeTag,
                            isUn,
                            type,
                            newValue
                        )

                        type.startsWith("item[") -> {
                            val idx = type.substringAfter("[").substringBefore("]")
                                .let { if (it == "+") -1 else it.toIntOrNull() ?: -1 }
                            onInlineArrayEdited?.invoke(parentKey, localeTag, isUn, idx, newValue)
                        }
                    }
                } else if (staticCol == ResourceColumn.UNTRANSLATABLE) {
                    onUntranslatableToggled?.invoke(parentKey, aValue as? Boolean ?: false)
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

    private fun setupColumns() {
        table.columnModel.getColumn(ResourceColumn.STATUS.index).cellRenderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                t: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                val comp = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column)
                if (comp is JLabel) {
                    comp.text = ""
                    comp.horizontalAlignment = CENTER
                    val status = value as? ResourceStatus
                    comp.icon = status?.icon
                    comp.toolTipText = status?.tooltip
                }
                return comp
            }
        }

        ResourceColumn.entries.forEach { colEnum ->
            val col = table.columnModel.getColumn(colEnum.index)
            when (colEnum) {
                ResourceColumn.STATUS -> col.apply { preferredWidth = 45; maxWidth = 45; minWidth = 45 }
                ResourceColumn.KEY -> col.apply { preferredWidth = 250; minWidth = 150 }
                ResourceColumn.USAGE, ResourceColumn.DELETE -> col.apply { preferredWidth = 50; maxWidth = 50 }
                ResourceColumn.UNTRANSLATABLE -> col.apply { preferredWidth = 110; maxWidth = 110 }
                ResourceColumn.TYPE -> col.apply { preferredWidth = 100; minWidth = 80 }
                ResourceColumn.DEFAULT_VALUE -> col.apply { preferredWidth = 300; minWidth = 150 }
            }
        }

        dynamicLocaleColumns.keys.forEach { idx ->
            table.columnModel.getColumn(idx).apply { preferredWidth = 300; minWidth = 150 }
        }
    }

    fun updateData(resources: List<XmlResource>) {
        val scope = project.service<KmpProjectScopeService>().coroutineScope

        scope.launch(Dispatchers.Default) {
            project.awaitSmartMode()

            val detectionService = project.service<LocaleDetectionService>()
            val activeLocales = detectionService.getActiveLocales()

            withContext(Dispatchers.EDT) {
                prepareTableModel(activeLocales)

                val newRows = mutableListOf<Array<Any?>>()
                val loadingStatus = ResourceStatus(
                    AllIcons.Actions.Refresh,
                    KmpResourcesBundle.message("ui.table.status.tooltip.analyzing")
                )

                resources.forEach { res ->
                    val mappedRows = ResourceRowMapper.mapToRows(res, activeLocales, loadingStatus)
                    newRows.addAll(mappedRows)
                }

                tableModel.setDataVector(newRows.toTypedArray(), getFullColumnIdentifiers(activeLocales))
                setupColumns()

                controller.validateResources(resources, activeLocales) { key, mainStatus, subStatuses ->
                    val mainRow = findModelRowForKey(key)
                    if (mainRow != -1) {
                        tableModel.setValueAt(mainStatus, mainRow, ResourceColumn.STATUS.index)
                        subStatuses.forEach { (subId, status) ->
                            updateSubItemStatus(mainRow, subId, status)
                        }
                    }
                }
            }
        }
    }

    private fun updateSubItemStatus(mainRow: Int, subId: String, status: ResourceStatus?) {
        for (i in 1..20) {
            val currRow = mainRow + i
            if (currRow >= tableModel.rowCount) break

            val keyAtRow = tableModel.getValueAt(currRow, ResourceColumn.KEY.index) as String
            if (keyAtRow.isNotEmpty()) break

            val typeAtRow = tableModel.getValueAt(currRow, ResourceColumn.TYPE.index) as String
            if (typeAtRow == subId) {
                tableModel.setValueAt(status, currRow, ResourceColumn.STATUS.index)
                break
            }
        }
    }

    private fun prepareTableModel(locales: List<LocaleInfo>) {
        dynamicLocaleColumns.clear()
        var currentIdx = ResourceColumn.entries.size
        locales.forEach { locale ->
            dynamicLocaleColumns[currentIdx] = locale.languageTag
            currentIdx++
        }
    }

    private fun getFullColumnIdentifiers(locales: List<LocaleInfo>): Array<String> {
        val titles = ResourceColumn.entries.map { KmpResourcesBundle.message(it.titleKey) }.toMutableList()

        locales.forEach { locale ->
            val flag = if (locale.flagEmoji.isNotEmpty()) "${locale.flagEmoji} " else ""
            titles.add("$flag${locale.displayName} (${locale.languageTag})")
        }

        return titles.toTypedArray()
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

    fun hasSelection(): Boolean = table.selectedRow >= 0

    fun triggerDeleteForSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            triggerDelete(table.convertRowIndexToModel(selectedRow))
        }
    }


    private fun getParentKeyNameForRow(startRow: Int): String? {
        var curr = startRow
        while (curr >= 0) {
            val name = tableModel.getValueAt(curr, ResourceColumn.KEY.index) as String
            if (name.isNotEmpty()) return name
            curr--
        }
        return null
    }

    private fun findModelRowForKey(key: String): Int {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getValueAt(i, ResourceColumn.KEY.index) == key) return i
        }
        return -1
    }


    private fun setupListeners() {
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val viewRow = table.rowAtPoint(e.point)
                val colIdx = table.columnAtPoint(e.point)
                if (viewRow < 0) return
                val modelRow = table.convertRowIndexToModel(viewRow)

                when (ResourceColumn.fromIndex(colIdx)) {
                    ResourceColumn.KEY -> getParentKeyNameForRow(modelRow)?.let { onEditRequested?.invoke(it) }
                    ResourceColumn.USAGE -> {
                        val key = tableModel.getValueAt(modelRow, ResourceColumn.KEY.index) as String
                        if (key.isNotEmpty()) onUsageRequested?.invoke(key)
                    }

                    ResourceColumn.DELETE -> triggerDelete(modelRow)
                    ResourceColumn.UNTRANSLATABLE -> {}
                    ResourceColumn.STATUS -> {}
                    ResourceColumn.DEFAULT_VALUE -> {}
                    ResourceColumn.TYPE -> {}
                    null -> {}
                }
            }
        })
    }

    private fun triggerDelete(modelRow: Int) {
        val type = tableModel.getValueAt(modelRow, ResourceColumn.TYPE.index) as? String ?: return
        val key = getParentKeyNameForRow(modelRow) ?: return
        val isSub = type in validQuantities || type.startsWith("item[")
        onDeleteRequested?.invoke(key, type, isSub)
    }
}