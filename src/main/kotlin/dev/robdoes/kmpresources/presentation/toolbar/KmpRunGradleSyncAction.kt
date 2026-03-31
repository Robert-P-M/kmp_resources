package dev.robdoes.kmpresources.presentation.toolbar

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

/**
 * Represents an IntelliJ IDEA action to trigger a Gradle synchronization process specific to Kotlin Multiplatform Projects (KMP).
 *
 * This action is designed to execute a Gradle task for generating resource accessors for the `commonMain` source set
 * in a KMP project. It is intended to simplify the management of resources across multiple platforms by programmatically
 * supporting the Gradle sync process within the IDE.
 *
 * Key functionality includes:
 * - Retrieving the current project and its context file.
 * - Invoking a Gradle task via the `triggerGenerateAccessors` helper method while ensuring seamless user experience.
 *
 * This action is marked as `DumbAware`, which indicates it can be invoked regardless of the IntelliJ indexer state,
 * such as when the IDE is synchronizing or rebuilding its indexes.
 */
internal class KmpRunGradleSyncAction : AnAction(), DumbAware {

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