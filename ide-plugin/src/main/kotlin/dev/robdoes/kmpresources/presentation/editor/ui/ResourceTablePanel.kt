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
import dev.robdoes.kmpresources.presentation.editor.ResourceRowMapper
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

/**
 * A panel that displays and manages a table of localization resources.
 *
 * This class is responsible for visualizing resource data and providing various capabilities
 * to interact with and manipulate localization resources, such as filtering, updating, and
 * handling user interactions across different resource types (e.g., strings, plurals, arrays).
 *
 * @property project The project associated with this panel.
 * @property scannerService A service used to scan and evaluate resource usage.
 * @property onInlineStringEdited Callback invoked when an inline string resource is edited.
 * @property onInlinePluralEdited Callback invoked when an inline plural resource is edited.
 * @property onInlineArrayEdited Callback invoked when an inline array resource is edited.
 * @property onUntranslatableToggled Callback invoked when the untranslatable state for a resource is toggled.
 * @property onEditRequested Callback invoked when an edit for a specific resource is requested.
 * @property onDeleteRequested Callback invoked when a delete operation for a specific resource is requested.
 * @property onUsageRequested Callback invoked when the usage of a specific resource is queried.
 * @property validQuantities List of valid quantities for plural resources.
 * @property tableModel The table model backing the resource table.
 * @property tableRowSorter The row sorter used to organize and display table rows.
 * @property table The table component displaying the resources.
 * @property dynamicLocaleColumns List of dynamically generated columns for locale-specific data.
 * @property controller The controller responsible for managing interactions and updating the panel state.
 */
internal class ResourceTablePanel(private val project: Project, private val scannerService: ResourceUsageService) :
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
                if (oldValue == aValue) return

                val type = getValueAt(row, ResourceColumn.TYPE.index) as? String ?: return
                val parentKey = getParentKeyNameForRow(row) ?: return
                val isUn = getValueAt(row, ResourceColumn.UNTRANSLATABLE.index) as? Boolean ?: false
                val localeTag = dynamicLocaleColumns[column]
                val staticCol = ResourceColumn.fromIndex(column)

                when {
                    staticCol == ResourceColumn.DEFAULT_VALUE || dynamicLocaleColumns.containsKey(column) -> {
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
                    }

                    staticCol == ResourceColumn.UNTRANSLATABLE -> {
                        super.setValueAt(aValue, row, column)
                        onUntranslatableToggled?.invoke(parentKey, aValue as? Boolean ?: false)
                    }

                    else -> super.setValueAt(aValue, row, column)
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

    /**
     * Scrolls the resource table to the row corresponding to the provided key, selects the row,
     * and makes it visible within the viewport. If a matching key is found, focus is also set
     * on the table.
     *
     * This method iterates through the table model to find a specific row where the "KEY" column
     * matches the given key. Once found, the table's selection is updated, the row is scrolled
     * into view, and user focus is requested on the table.
     *
     * @param key The unique key of the row to scroll to and select. Must match the value in the "KEY" column.
     */
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

    /**
     * Configures the columns of the resource table, including their rendering components, widths,
     * and other properties. This method ensures that each column is presented and behaves
     * appropriately based on its intended use.
     *
     * Responsibilities:
     * - Sets up a custom cell renderer for the "STATUS" column to display icons and tooltips
     *   based on the resource status.
     * - Adjusts the preferred, minimum, and maximum widths for predefined columns, such as
     *   "STATUS", "KEY", and "DEFAULT_VALUE".
     * - Dynamically configures the widths for locale-specific columns using the `dynamicLocaleColumns`.
     * - Ensures that each column is appropriately aligned and ready for user interactions.
     */
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

    /**
     * Updates the data displayed in the resource table by processing the provided list of XML-based resources.
     *
     * The method performs the following actions:
     * - Waits for the IntelliJ IDEA indexing process (dumb mode) to complete, ensuring the project is in smart mode.
     * - Retrieves the active locales for resource processing.
     * - Prepares the table model to accommodate the active locales.
     * - Transforms each provided resource into table rows using a row-mapping utility.
     * - Updates the table model with the newly generated data and reconfigures the table columns.
     * - Validates the resources and updates their statuses in the table, including any sub-items.
     *
     * This method is executed on a separate coroutine scope to ensure non-blocking behavior. UI updates are switched
     * to the event dispatch thread (EDT) as needed.
     *
     * @param resources A list of [XmlResource] objects representing XML-based resources to be processed and displayed
     * in the resource table.
     */
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

    /**
     * Updates the status of a sub-item in the resource table based on the provided main row,
     * sub-item identifier, and the new status value. The method iterates through the rows
     * following the specified main row and updates the status for the first matching sub-item.
     *
     * The row is considered a match if the "TYPE" column value matches the given `subId`.
     * The update stops as soon as the first match is found, or when reaching the end of the table
     * or encountering a non-empty value in the "KEY" column of a row.
     *
     * @param mainRow The starting row index from which to begin searching for the sub-item.
     *                This is usually the row of the main item associated with the sub-items.
     * @param subId The identifier of the sub-item to locate in the "TYPE" column of the table.
     * @param status The new status of the sub-item, represented as an instance of [ResourceStatus].
     *               If null, no status value will be set.
     */
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

    /**
     * Prepares the table model by configuring dynamic locale columns based on the provided list of locales.
     *
     * This method clears existing dynamic locale columns and maps the language tags of the given locales
     * to consecutive column indices, starting after the predefined columns.
     *
     * @param locales A list of [LocaleInfo] objects representing the active locales to be displayed
     *        in the resource table. Each locale contributes a dynamic column to the table.
     */
    private fun prepareTableModel(locales: List<LocaleInfo>) {
        dynamicLocaleColumns.clear()
        var currentIdx = ResourceColumn.entries.size
        locales.forEach { locale ->
            dynamicLocaleColumns[currentIdx] = locale.languageTag
            currentIdx++
        }
    }

    /**
     * Generates an array of full column identifiers for the resource table.
     *
     * The method combines the base column titles from the predefined resource columns with
     * the locale-specific column titles. Locale-specific columns include the locale's flag emoji (if available),
     * display name, and language tag, formatted accordingly.
     *
     * @param locales A list of [LocaleInfo] objects representing the active locales. Each locale contributes
     *        a dynamic column to the resulting list.
     * @return An array of strings containing the complete set of column identifiers, including both
     *         static resource columns and dynamic locale-specific columns.
     */
    private fun getFullColumnIdentifiers(locales: List<LocaleInfo>): Array<String> {
        val titles = ResourceColumn.entries.map { KmpResourcesBundle.message(it.titleKey) }.toMutableList()

        locales.forEach { locale ->
            val flag = if (locale.flagEmoji.isNotEmpty()) "${locale.flagEmoji} " else ""
            titles.add("$flag${locale.displayName} (${locale.languageTag})")
        }

        return titles.toTypedArray()
    }

    /**
     * Applies a filter to the resource table based on the given criteria.
     * The method updates the row filter of the table's row sorter, filtering rows based
     * on the value of the "TYPE" column. Available filter options include:
     * "ALL" (no filtering), "STRINGS" (string type rows), "PLURALS" (plural resource types),
     * and "ARRAYS" (arrays including string arrays and indexed items).
     *
     * @param filter A string representing the filter criteria. Valid values include:
     * - "ALL": Displays all rows without filtering.
     * - "STRINGS": Displays rows with "string" type.
     * - "PLURALS": Displays rows with plural types (e.g., "plurals" or specific quantities).
     * - "ARRAYS": Displays rows belonging to array types (e.g., "string-array" or indexed items).
     */
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

    /**
     * Checks whether there is any row currently selected in the resource table.
     *
     * @return `true` if a row is selected (i.e., `table.selectedRow >= 0`), otherwise `false`.
     */
    fun hasSelection(): Boolean = table.selectedRow >= 0

    /**
     * Triggers the deletion process for the currently selected row in the resource table.
     *
     * This method checks if a valid row is selected in the table. If a valid selection exists,
     * it converts the table row index to the underlying model index and invokes the deletion
     * logic for the corresponding row.
     *
     * The deletion process is managed by `triggerDelete`, which performs the necessary operations
     * to handle the removal of the resource tied to the selected row.
     *
     * Preconditions:
     * - The table must have a valid row selected (`table.selectedRow >= 0`).
     */
    fun triggerDeleteForSelectedRow() {
        val selectedRow = table.selectedRow
        if (selectedRow >= 0) {
            triggerDelete(table.convertRowIndexToModel(selectedRow))
        }
    }

    /**
     * Retrieves the parent key name for a specified row in the resource table.
     * The method iterates upwards in the table starting from the given row index,
     * checking the "KEY" column for a non-empty value. The first encountered
     * non-empty key value is returned as the parent key name. If no such value
     * is found, the method returns `null`.
     *
     * @param startRow The index of the row to start searching from. The search moves
     *                 upwards from this row towards the beginning of the table.
     * @return The parent key name as a `String`, or `null` if no parent key is found
     *         or if the specified row index is invalid.
     */
    private fun getParentKeyNameForRow(startRow: Int): String? {
        var curr = startRow
        while (curr >= 0) {
            val name = tableModel.getValueAt(curr, ResourceColumn.KEY.index) as String
            if (name.isNotEmpty()) return name
            curr--
        }
        return null
    }

    /**
     * Finds the row index in the table model that corresponds to the specified key.
     *
     * @param key The key to search for in the table model.
     * @return The index of the row where the key is found, or -1 if the key is not present.
     */
    private fun findModelRowForKey(key: String): Int {
        for (i in 0 until tableModel.rowCount) {
            if (tableModel.getValueAt(i, ResourceColumn.KEY.index) == key) return i
        }
        return -1
    }

    /**
     * Sets up listeners for the table component to handle user interactions such as mouse clicks.
     *
     * The method adds a `MouseAdapter` to the table, enabling the following functionality:
     * - Identifies the clicked row and column in the table.
     * - Converts the view row index to a model row index.
     * - Performs different actions depending on the column clicked:
     *   - If the "KEY" column is clicked, requests to edit the associated key.
     *   - If the "USAGE" column is clicked, invokes a callback to handle usage actions for the selected key.
     *   - If the "DELETE" column is clicked, triggers the deletion operation for the selected row.
     */
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

    /**
     * Triggers a deletion operation for the specified row in the table model.
     *
     * @param modelRow The index of the row in the table model to be deleted.
     *                 Must be a valid row index.
     */
    private fun triggerDelete(modelRow: Int) {
        val type = tableModel.getValueAt(modelRow, ResourceColumn.TYPE.index) as? String ?: return
        val key = getParentKeyNameForRow(modelRow) ?: return
        val isSub = type in validQuantities || type.startsWith("item[")
        onDeleteRequested?.invoke(key, type, isSub)
    }
}