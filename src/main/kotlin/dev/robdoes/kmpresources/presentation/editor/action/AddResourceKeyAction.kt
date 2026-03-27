package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

class AddResourceKeyAction(
    private val onAddRequested: () -> Unit
) : AnAction(
    KmpResourcesBundle.message("action.table.add.key.text"),
    KmpResourcesBundle.message("action.table.add.key.desc"),
    AllIcons.General.Add
) {
    override fun actionPerformed(e: AnActionEvent) = onAddRequested()
}


