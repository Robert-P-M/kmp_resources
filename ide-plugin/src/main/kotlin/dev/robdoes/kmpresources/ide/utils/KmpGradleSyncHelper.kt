package dev.robdoes.kmpresources.ide.utils

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import kotlinx.coroutines.launch

/**
 * Helper object that assists with synchronizing Kotlin Multiplatform projects in a Gradle environment.
 *
 * This utility is specifically created for situations where additional Gradle tasks need to be triggered
 * within the context of an IntelliJ IDEA project. It ensures that the required tasks execute correctly
 * and provides a callback mechanism upon successful task completion.
 */
internal object KmpGradleSyncHelper {

    /**
     * Triggers the Gradle task to generate resource accessors for the `commonMain` source set within a Kotlin Multiplatform project.
     * This method ensures that the task execution is managed either synchronously or asynchronously depending on the environment.
     *
     * @param project The current IntelliJ IDEA project in which the Gradle task will be executed.
     * @param contextFile The virtual file representing the context or location where the task is to be triggered.
     * @param onSuccess A callback function that is executed upon the successful completion of the Gradle task. Defaults to a no-op.
     */
    fun triggerGenerateAccessors(project: Project, contextFile: VirtualFile, onSuccess: () -> Unit = {}) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            onSuccess()
            return
        }

        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val module = readAction { ModuleUtilCore.findModuleForFile(contextFile, project) }
            val basePath = readAction {
                module?.let { ExternalSystemApiUtil.getExternalProjectPath(it) }
            } ?: project.basePath ?: return@launch

            val settings = ExternalSystemTaskExecutionSettings().apply {
                externalProjectPath = basePath
                taskNames = listOf("generateResourceAccessorsForCommonMain")
                externalSystemIdString = "GRADLE"
            }

            ExternalSystemUtil.runTask(
                settings, DefaultRunExecutor.EXECUTOR_ID, project, ProjectSystemId("GRADLE"),
                object : TaskCallback {
                    override fun onSuccess() {
                        onSuccess()
                    }

                    override fun onFailure() {}
                },
                ProgressExecutionMode.IN_BACKGROUND_ASYNC, false
            )
        }
    }
}