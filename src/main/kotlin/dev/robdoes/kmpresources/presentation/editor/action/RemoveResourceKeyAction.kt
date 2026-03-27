package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

class RemoveResourceKeyAction(
    private val hasSelection: () -> Boolean,
    private val onRemoveRequested: () -> Unit
) : AnAction(
    KmpResourcesBundle.message("action.table.remove.key.text"),
    KmpResourcesBundle.message("action.table.remove.key.desc"),
    AllIcons.General.Remove
) {
    override fun actionPerformed(e: AnActionEvent) = onRemoveRequested()

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = hasSelection()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT
}