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

class AddLocaleAction(
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