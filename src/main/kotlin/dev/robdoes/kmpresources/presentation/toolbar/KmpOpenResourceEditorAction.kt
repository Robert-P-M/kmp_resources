package dev.robdoes.kmpresources.presentation.toolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.presentation.editor.KmpResourceVirtualFile
import kotlinx.coroutines.launch


class KmpOpenResourceEditorAction : AnAction(
    KmpResourcesBundle.message("action.toolbar.open.editor.text"),
    KmpResourcesBundle.message("action.toolbar.open.editor.desc"),
    AllIcons.FileTypes.Xml,
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val issueService = project.service<ResourceIssueService>()
            val files = issueService.findAllResourceFiles()
            val defaultFile = files.firstOrNull() ?: return@launch
            val modulePath = defaultFile.path
                .substringAfter(project.basePath ?: "")
                .substringBefore("src")
                .replace("\\\\", "/")
                .removePrefix("/")
            val kmpVirtualFile = KmpResourceVirtualFile(modulePath, defaultFile)
            com.intellij.openapi.application.ApplicationManager.getApplication()
                .invokeLater {
                    FileEditorManager.getInstance(project).openFile(kmpVirtualFile, true)
                }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}


