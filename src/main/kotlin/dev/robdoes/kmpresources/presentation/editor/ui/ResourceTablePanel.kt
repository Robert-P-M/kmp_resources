package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import dev.robdoes.kmpresources.core.application.service.LocaleDetectionService
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
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
        fun fromIndex(index: Int): ResourceColumn? = entries.find { it.index == index }
    }
}

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

    private val validQuantities = listOf("zero", "one", "two", "few", "many", "other")
    private val tableModel: DefaultTableModel
    private val tableRowSorter: TableRowSorter<DefaultTableModel>
    private val table: JBTable

    private var dynamicLocaleColumns = mutableMapOf<Int, String>()

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
                    comp.horizontalAlignment = SwingConstants.CENTER
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
        scope.launch {
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
                    addRowForResource(res, newRows, loadingStatus, activeLocales)
                }

                tableModel.setDataVector(newRows.toTypedArray(), getFullColumnIdentifiers(activeLocales))
                setupColumns()
                startAsyncValidation(resources, activeLocales)
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
        locales.forEach { titles.add("${it.flagEmoji} ${it.languageTag}".trim()) }
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

    private fun addRowForResource(
        res: XmlResource,
        rows: MutableList<Array<Any?>>,
        status: ResourceStatus,
        locales: List<LocaleInfo>
    ) {

        fun getValueForLocale(
            resource: XmlResource,
            localeTag: String?,
            quantity: String? = null,
            index: Int? = null
        ): String {
            return when (resource) {
                is StringResource -> resource.values[localeTag] ?: ""
                is PluralsResource -> {
                    val items = resource.localizedItems[localeTag] ?: emptyMap()
                    if (quantity != null) items[quantity] ?: "" else ""
                }

                is StringArrayResource -> {
                    val items = resource.localizedItems[localeTag] ?: emptyList()
                    if (index != null && index in items.indices) items[index] else ""
                }
            }
        }

        fun createBaseRow(
            key: String,
            type: String,
            isUn: Boolean,
            quantity: String? = null,
            index: Int? = null
        ): Array<Any?> {
            val row = arrayOfNulls<Any?>(ResourceColumn.entries.size + locales.size)
            row[ResourceColumn.STATUS.index] = status
            row[ResourceColumn.KEY.index] = key
            row[ResourceColumn.USAGE.index] = if (key.isNotEmpty()) AllIcons.Actions.Search else null
            row[ResourceColumn.DELETE.index] = AllIcons.General.Remove
            row[ResourceColumn.UNTRANSLATABLE.index] = isUn
            row[ResourceColumn.TYPE.index] = type

            row[ResourceColumn.DEFAULT_VALUE.index] = getValueForLocale(res, null, quantity, index)

            locales.forEachIndexed { i, locale ->
                val colIdx = ResourceColumn.entries.size + i
                row[colIdx] = getValueForLocale(res, locale.languageTag, quantity, index)
            }

            return row
        }

        when (res) {
            is StringResource -> {
                rows.add(createBaseRow(res.key, "string", res.isUntranslatable))
            }

            is PluralsResource -> {
                rows.add(createBaseRow(res.key, "plurals", res.isUntranslatable))
                validQuantities.forEach { q ->
                    val subRow = createBaseRow("", q, false, quantity = q)
                    subRow[ResourceColumn.USAGE.index] = null
                    rows.add(subRow)
                }
            }

            is StringArrayResource -> {
                rows.add(createBaseRow(res.key, "string-array", res.isUntranslatable))
                val maxItems = locales.map { res.localizedItems[it.languageTag]?.size ?: 0 }.maxOrNull() ?: 0
                val defaultSize = res.localizedItems[null]?.size ?: 0
                val finalMax = maxOf(maxItems, defaultSize)

                for (i in 0 until finalMax) {
                    val subRow = createBaseRow("", "item[$i]", false, index = i)
                    subRow[ResourceColumn.USAGE.index] = null
                    rows.add(subRow)
                }
                rows.add(createBaseRow("", "item[+]", false).apply {
                    this[ResourceColumn.DELETE.index] = null
                })
            }
        }
    }

    private fun startAsyncValidation(resources: List<XmlResource>, locales: List<LocaleInfo>) {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            DumbService.getInstance(project).waitForSmartMode()

            resources.forEach { res ->
                val isUsed = scannerService.isResourceUsed(res.key)

                val missingTranslation = if (res.isUntranslatable) {
                    false
                } else {
                    locales.any { locale ->
                        val tag = locale.languageTag
                        when (res) {
                            is StringResource -> res.values[tag].isNullOrBlank()
                            is PluralsResource -> {
                                val defaultKeys = res.localizedItems[null]?.keys ?: emptySet()
                                val localeItems = res.localizedItems[tag] ?: emptyMap()
                                defaultKeys.any { localeItems[it].isNullOrBlank() }
                            }

                            is StringArrayResource -> {
                                val defaultSize = res.localizedItems[null]?.size ?: 0
                                val localeItems = res.localizedItems[tag] ?: emptyList()
                                localeItems.size < defaultSize || localeItems.any { it.isBlank() }
                            }
                        }
                    }
                }

                val finalStatus = when {
                    !isUsed -> ResourceStatus(
                        AllIcons.General.Error,
                        KmpResourcesBundle.message("ui.table.status.tooltip.unused")
                    )

                    missingTranslation -> ResourceStatus(
                        AllIcons.General.Warning,
                        KmpResourcesBundle.message("ui.table.status.tooltip.missing_translation")
                    )

                    else -> ResourceStatus(
                        AllIcons.General.InspectionsOK,
                        KmpResourcesBundle.message("ui.table.status.tooltip.ok")
                    )
                }

                withContext(Dispatchers.EDT) {
                    val mainRow = findModelRowForKey(res.key)
                    if (mainRow != -1) {
                        tableModel.setValueAt(finalStatus, mainRow, ResourceColumn.STATUS.index)

                        if (res is PluralsResource) {
                            val defaultKeys = res.localizedItems[null]?.keys ?: emptySet()
                            validQuantities.forEachIndexed { i, q ->
                                val subRowIdx = mainRow + 1 + i
                                val subStatus = if (q !in defaultKeys) {
                                    null
                                } else {
                                    val isMissing = !res.isUntranslatable && locales.any { l ->
                                        res.localizedItems[l.languageTag]?.get(q).isNullOrBlank()
                                    }
                                    when {
                                        !isUsed -> ResourceStatus(
                                            AllIcons.General.Error,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.unused")
                                        )

                                        isMissing -> ResourceStatus(
                                            AllIcons.General.Warning,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.missing_translation")
                                        )

                                        else -> ResourceStatus(
                                            AllIcons.General.InspectionsOK,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.ok")
                                        )
                                    }
                                }
                                tableModel.setValueAt(subStatus, subRowIdx, ResourceColumn.STATUS.index)
                            }
                        }

                        if (res is StringArrayResource) {
                            val defaultSize = res.localizedItems[null]?.size ?: 0
                            val maxItems =
                                locales.map { res.localizedItems[it.languageTag]?.size ?: 0 }.maxOrNull() ?: 0
                            val finalMax = maxOf(maxItems, defaultSize)

                            for (i in 0 until finalMax) {
                                val subRowIdx = mainRow + 1 + i
                                val subStatus = if (i >= defaultSize) {
                                    null
                                } else {
                                    val isMissing = !res.isUntranslatable && locales.any { l ->
                                        val items = res.localizedItems[l.languageTag] ?: emptyList()
                                        i >= items.size || items[i].isBlank()
                                    }
                                    when {
                                        !isUsed -> ResourceStatus(
                                            AllIcons.General.Error,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.unused")
                                        )

                                        isMissing -> ResourceStatus(
                                            AllIcons.General.Warning,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.missing_translation")
                                        )

                                        else -> ResourceStatus(
                                            AllIcons.General.InspectionsOK,
                                            KmpResourcesBundle.message("ui.table.status.tooltip.ok")
                                        )
                                    }
                                }
                                tableModel.setValueAt(subStatus, subRowIdx, ResourceColumn.STATUS.index)
                            }
                            tableModel.setValueAt(null, mainRow + 1 + finalMax, ResourceColumn.STATUS.index)
                        }
                    }
                }
            }
        }
    }

    private fun findModelRowForKey(key: String): Int {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getValueAt(i, ResourceColumn.KEY.index) == key) return i
        }
        return -1
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