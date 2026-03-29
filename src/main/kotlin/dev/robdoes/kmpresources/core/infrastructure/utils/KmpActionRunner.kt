package dev.robdoes.kmpresources.core.infrastructure.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiFile

object KmpActionRunner {

    fun <T> runRead(action: () -> T): T {
        return ApplicationManager.getApplication().runReadAction(Computable { action() })
    }

    fun runWriteCommand(
        project: Project,
        commandName: String,
        psiFiles: List<PsiFile>,
        action: () -> Unit
    ) {
        WriteCommandAction.runWriteCommandAction(
            project,
            commandName,
            "KMP Resources",
            action,
            *psiFiles.toTypedArray()
        )
    }
}