package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import dev.robdoes.kmpresources.presentation.editor.model.ResourceFilter
import javax.swing.JComponent

/**
 * Represents an action that allows filtering resources through a dropdown combo box in the UI.
 *
 * This action dynamically updates its options and current selection based on the provided
 * filter input and selection behavior. It uses the `ResourceFilter` enumeration as the source
 * of filter options and updates its UI text to reflect the currently selected filter.
 *
 * @property getCurrentFilter A function that returns the currently selected resource filter.
 * @property onFilterSelected A function that handles logic when a filter is selected from the dropdown.
 */
internal class FilterResourceAction(
    private val getCurrentFilter: () -> ResourceFilter,
    private val onFilterSelected: (ResourceFilter) -> Unit
) : ComboBoxAction() {

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        return DefaultActionGroup().apply {
            ResourceFilter.entries.forEach { filter ->
                add(object : AnAction(filter.getDisplayText()) {
                    override fun actionPerformed(e: AnActionEvent) {
                        onFilterSelected(filter)
                    }
                })
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = getCurrentFilter().getDisplayText()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}