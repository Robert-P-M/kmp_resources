package dev.robdoes.kmpresources.presentation.editor.controller

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.awaitSmartMode
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.ResourceState
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.usecase.CalculateResourceStateUseCase
import dev.robdoes.kmpresources.presentation.editor.model.ResourceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResourceTablePaneController(
    private val project: Project,
    private val scannerService: ResourceUsageService
) {
    private val calculateResourceStateUseCase = CalculateResourceStateUseCase(scannerService)
    fun validateResources(
        resources: List<XmlResource>,
        locales: List<LocaleInfo>,
        onStatusUpdated: (key: String, status: ResourceStatus, subStatuses: Map<String, ResourceStatus?>) -> Unit
    ) {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            project.awaitSmartMode()

            resources.forEach { res ->
                val evaluation = calculateResourceStateUseCase(res, locales)

                val mainStatus = mapToUiStatus(evaluation.mainState)
                val subStatuses = evaluation.subStates.mapValues { (_, state) ->
                    state?.let { mapToUiStatus(it) }
                }

                withContext(Dispatchers.EDT) {
                    onStatusUpdated(res.key, mainStatus, subStatuses)
                }
            }
        }
    }

    private fun mapToUiStatus(state: ResourceState): ResourceStatus {
        return when (state) {
            ResourceState.UNUSED -> ResourceStatus(
                AllIcons.General.Error,
                KmpResourcesBundle.message("ui.table.status.tooltip.unused")
            )

            ResourceState.MISSING_TRANSLATION -> ResourceStatus(
                AllIcons.General.Warning,
                KmpResourcesBundle.message("ui.table.status.tooltip.missing_translation")
            )

            ResourceState.OK -> ResourceStatus(
                AllIcons.General.InspectionsOK,
                KmpResourcesBundle.message("ui.table.status.tooltip.ok")
            )
        }
    }
}