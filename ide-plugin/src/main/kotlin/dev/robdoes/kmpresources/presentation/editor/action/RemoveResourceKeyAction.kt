package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

/**
 * Action for removing a resource key in a table context.
 *
 * This action handles the removal of selected resource keys from a table by invoking the
 * provided callback when triggered. It also dynamically updates its enabled state
 * based on the current selection status.
 *
 * @constructor
 * @param hasSelection Lambda function returning a boolean that specifies if a selection is present.
 * This determines whether the action should be enabled or disabled.
 * @param onRemoveRequested Lambda function invoked to process the removal request
 * when the action is performed.
 */
internal class RemoveResourceKeyAction(
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