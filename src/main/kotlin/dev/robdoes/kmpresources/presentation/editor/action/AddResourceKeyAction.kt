package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

/**
 * Represents an action to add a new resource key in the resource table.
 *
 * @constructor Creates an instance of AddResourceKeyAction.
 * @param onAddRequested A callback invoked when the action is performed.
 */
internal class AddResourceKeyAction(
    private val onAddRequested: () -> Unit
) : AnAction(
    KmpResourcesBundle.message("action.table.add.key.text"),
    KmpResourcesBundle.message("action.table.add.key.desc"),
    AllIcons.General.Add
) {
    override fun actionPerformed(e: AnActionEvent) = onAddRequested()
}


