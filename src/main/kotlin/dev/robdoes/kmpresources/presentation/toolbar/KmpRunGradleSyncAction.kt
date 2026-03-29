package dev.robdoes.kmpresources.presentation.toolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

class KmpRunGradleSyncAction : AnAction(
    KmpResourcesBundle.message("action.toolbar.sync.text"),
    KmpResourcesBundle.message("action.toolbar.sync.desc"),
    AllIcons.Actions.Refresh,
), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: project.basePath
                ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?: return

        KmpGradleSyncHelper.triggerGenerateAccessors(
            project = project,
            contextFile = contextFile,
            onSuccess = {}
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}