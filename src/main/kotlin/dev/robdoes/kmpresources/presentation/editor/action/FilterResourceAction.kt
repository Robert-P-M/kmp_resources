package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import dev.robdoes.kmpresources.presentation.editor.model.ResourceFilter
import javax.swing.JComponent

class FilterResourceAction(
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