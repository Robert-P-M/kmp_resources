package dev.robdoes.kmpresources.ide.utils

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object KmpGradleSyncHelper {

    fun triggerGenerateAccessors(project: Project, contextFile: VirtualFile, onSuccess: () -> Unit = {}) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            onSuccess()
            return
        }

        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val module = readAction { ModuleUtilCore.findModuleForFile(contextFile, project) } ?: return@launch
            val basePath =
                readAction { ExternalSystemApiUtil.getExternalProjectPath(module) ?: project.basePath } ?: return@launch

            val settings = ExternalSystemTaskExecutionSettings().apply {
                externalProjectPath = basePath
                taskNames = listOf("generateResourceAccessorsForCommonMain")
                externalSystemIdString = "GRADLE"
            }

            withContext(Dispatchers.EDT) {
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
}