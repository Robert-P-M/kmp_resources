package dev.robdoes.kmpresources.presentation.toolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.presentation.editor.KmpResourceVirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Represents an action to open a custom resource editor within the IntelliJ platform.
 * This action is intended for use in multi-platform projects to facilitate resource file management and editing.
 *
 * It leverages project services to identify resource files, configure a custom virtual file representation,
 * and open the file within the editor.
 */
internal class KmpOpenResourceEditorAction : AnAction() {
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
            withContext(Dispatchers.EDT) {
                FileEditorManager.getInstance(project).openFile(kmpVirtualFile, true)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}


