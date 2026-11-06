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

/**
 * Controller responsible for managing the resource table pane and validating resource states.
 *
 * This class orchestrates the interaction between the resource validation logic and the UI update mechanism.
 * It utilizes services and use cases to evaluate the current state of resources and provides status updates
 * to the UI in an asynchronous manner.
 *
 * @constructor Creates an instance of the controller with the provided project context and resource usage service.
 * @param project The project context for which this controller operates.
 * @param scannerService The service for assessing resource usage within the project.
 */
internal class ResourceTablePaneController(
    private val project: Project,
    private val scannerService: ResourceUsageService
) {
    private val calculateResourceStateUseCase = CalculateResourceStateUseCase(scannerService)

    /**
     * Validates a collection of XML-based resources against a set of locales and provides updates on their status.
     *
     * This method iterates through the provided resources, evaluates their state for the given locales,
     * and invokes a callback function to report the computed statuses. The computation is executed
     * asynchronously within a coroutine context.
     *
     * @param resources A list of XML-based resources to validate. Each resource represents a unique
     * element in the project's localization structure.
     * @param locales A list of locale information to use during validation. Each locale provides
     * data such as language tags, display names, and flag emojis relevant to localization.
     * @param onStatusUpdated A callback function invoked with the updated status of each resource.
     * It provides the following parameters:
     * - `key`: A unique identifier for the resource.
     * - `status`: The main status of the resource, represented as a [ResourceStatus].
     * - `subStatuses`: A map of statuses for resource variants, keyed by variant identifiers,
     * where the value is a [ResourceStatus] or `null` if no status is assigned.
     */
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

    /**
     * Maps a given resource state to a corresponding UI status.
     *
     * This method converts an instance of [ResourceState] into a [ResourceStatus], which
     * includes an icon and a tooltip message used for displaying the resource's status in the UI.
     *
     * @param state The input resource state to be mapped. It indicates the current condition
     *        of the resource, such as being unused, missing translations, or being in a valid state.
     * @return A [ResourceStatus] object that represents the UI equivalent of the provided [ResourceState].
     */
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