package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.core.shared.LocaleProvider
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * A custom action that provides functionality to add a new locale to a table.
 *
 * This action extends `ComboBoxAction` and displays a popup allowing users to search for and select
 * a locale from a list of available options. Selected locales can then be added through a provided
 * callback mechanism.
 *
 * @constructor
 * Creates an instance of `AddLocaleAction`.
 *
 * @param onLocaleSelected Callback invoked when a locale is selected.
 */
internal class AddLocaleAction(
    private val onLocaleSelected: (LocaleInfo) -> Unit,
) : ComboBoxAction() {

    var existingLocales: Set<String> = emptySet()

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = KmpResourcesBundle.message("action.table.add.locale.text")
        e.presentation.description = KmpResourcesBundle.message("action.table.add.locale.desc")
        e.presentation.icon = AllIcons.General.Add
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext) = DefaultActionGroup()

    override fun createActionPopup(
        context: DataContext,
        component: JComponent,
        disposeCallback: Runnable?,
    ): JBPopup {
        val allLocales = LocaleProvider.getAvailableLocales()
        val popupPanel = buildSearchableLocalePanel(
            allLocales = allLocales,
            onLocaleSelected = { locale ->
                onLocaleSelected(locale)
            },
            disposeCallback = disposeCallback,
        )

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(popupPanel, popupPanel.getClientProperty("searchField") as? JComponent)
            .setTitle(KmpResourcesBundle.message("action.table.add.locale.popup.title"))
            .setResizable(false)
            .setMovable(false)
            .setRequestFocus(true)
            .setMinSize(Dimension(360, 60))
            .createPopup()

        popupPanel.putClientProperty("popupRef", popup)
        disposeCallback?.let {
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) = it.run()
            })
        }
        return popup
    }

    /**
     * Builds a panel containing a searchable list of locales.
     *
     * This method creates a user interface panel that includes a search field and a scrollable list of locales.
     * Users can search for locales by name or language tag, and select any locale that is not already present
     * in the `existingLocales`. When a locale is selected, the provided callback is invoked.
     *
     * @param allLocales The list of all available locales to display and search from.
     * @param onLocaleSelected A callback function invoked when a locale is selected by the user.
     *                         The selected locale is passed as a parameter to this function.
     * @param disposeCallback An optional callback that is triggered when the panel needs to be disposed or closed.
     * @return A `JPanel` containing the searchable locale list and associated UI components.
     */
    private fun buildSearchableLocalePanel(
        allLocales: List<LocaleInfo>,
        onLocaleSelected: (LocaleInfo) -> Unit,
        disposeCallback: Runnable?,
    ): JPanel {
        val searchField = SearchTextField(false).apply {
            textEditor.emptyText.text = "Search language or country…"
            border = JBUI.Borders.empty(4, 8)
        }

        val listModel = javax.swing.DefaultListModel<LocaleInfo>()
        val list = JBList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = LocaleListCellRenderer(existingLocales)
        }

        fun refreshList(query: String) {
            listModel.clear()
            val q = query.trim().lowercase()
            val (general, regional) = allLocales.partition { !it.languageTag.contains('-') }
            sequenceOf(general, regional)
                .flatten()
                .filter { locale ->
                    q.isEmpty() ||
                            locale.displayName.lowercase().contains(q) ||
                            locale.languageTag.lowercase().contains(q)
                }
                .forEach { listModel.addElement(it) }
        }

        refreshList("")

        searchField.addDocumentListener(object : com.intellij.ui.DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                refreshList(searchField.text)
            }
        })

        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selected = list.selectedValue ?: return@addListSelectionListener
                if (existingLocales.contains(selected.languageTag)) return@addListSelectionListener
                onLocaleSelected(selected)
                val popupRef = (list.parent?.parent?.parent as? JPanel)
                    ?.getClientProperty("popupRef") as? JBPopup
                popupRef?.cancel()
            }
        }

        val ROW_HEIGHT = 26
        val MAX_VISIBLE = 10
        val scrollPane = JBScrollPane(list).apply {
            preferredSize = Dimension(360, ROW_HEIGHT * MAX_VISIBLE)
            border = BorderFactory.createEmptyBorder()
        }

        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            preferredSize = Dimension(360, ROW_HEIGHT * MAX_VISIBLE + 40)
            add(searchField, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            putClientProperty("searchField", searchField)
        }
    }

    /**
     * Custom cell renderer for displaying a list of locales in a `JList` component.
     *
     * This class extends `DefaultListCellRenderer` to provide a customized rendering
     * of `LocaleInfo` objects in a list. It visually displays the locale's flag emoji,
     * display name, and language tag. If a locale's language tag exists in the
     * provided `existingLocales` set, it is rendered as disabled and grayed out.
     *
     * @constructor Creates a new instance of the renderer.
     * @param existingLocales A set of language tags representing locales that are
     *                        already selected or unavailable for selection.
     */
    private class LocaleListCellRenderer(
        private val existingLocales: Set<String>,
    ) : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): java.awt.Component {
            val label = super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus,
            ) as JLabel
            val locale = value as? LocaleInfo ?: return label
            val flag = locale.flagEmoji.takeIf { it.isNotEmpty() }?.let { "$it " } ?: ""
            label.text = "$flag${locale.displayName}  ${locale.languageTag}"
            if (existingLocales.contains(locale.languageTag)) {
                label.foreground = JBColor.GRAY
                label.isEnabled = false
            }
            return label
        }
    }
}