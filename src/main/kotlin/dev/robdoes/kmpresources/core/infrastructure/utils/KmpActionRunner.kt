package dev.robdoes.kmpresources.core.infrastructure.utils

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * A utility object for executing write commands within the IntelliJ Platform.
 * This is designed to perform modifications on PSI files and other project-related data
 * in a thread-safe manner by wrapping the requested operation in a write command action.
 */
internal object KmpActionRunner {


    /**
     * Executes a write command within an IntelliJ IDEA project context.
     *
     * This method runs the provided action inside a write command, ensuring that any modifications
     * to the PSI (Program Structure Interface) or virtual files are performed safely
     * within a write-access block. The write command is associated with the given project,
     * allows undo by specifying a command name, and targets a collection of PSI files.
     *
     * @param project The IntelliJ IDEA project within which the write command is executed.
     * @param commandName The name of the write command, used for undo/redo operations in the IDE.
     * @param psiFiles The list of PSI files to be targeted during the write command.
     * @param action The action to be executed inside the write command. This action contains
     *        the logic for modifying the files or other project elements.
     */
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