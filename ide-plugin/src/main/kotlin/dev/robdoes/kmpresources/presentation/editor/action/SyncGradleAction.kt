package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

/**
 * An action that triggers a Gradle synchronization task for a specified project and virtual file,
 * specifically targeting resource accessor generation in a Kotlin Multiplatform environment.
 *
 * This action is registered with the IntelliJ Platform, providing a convenient way to refresh
 * Gradle resources and ensure they are up-to-date.
 *
 * @constructor Creates an instance of the SyncGradleAction.
 * @param project The IntelliJ IDEA project within which the Gradle synchronization will be performed.
 * @param file The virtual file that serves as the context for the Gradle synchronization task.
 */
internal class SyncGradleAction(
    private val project: Project,
    private val file: VirtualFile
) : AnAction(
    KmpResourcesBundle.message("action.sync.gradle.text"),
    KmpResourcesBundle.message("action.sync.gradle.description"),
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
    }
}