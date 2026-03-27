package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

class SyncGradleAction(
    private val project: Project,
    private val file: VirtualFile
) : AnAction(
    KmpResourcesBundle.message("action.sync.gradle.text"),
    KmpResourcesBundle.message("action.sync.gradle.desc"),
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
    }
}