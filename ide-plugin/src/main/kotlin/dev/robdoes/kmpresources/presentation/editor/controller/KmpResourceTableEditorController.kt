package dev.robdoes.kmpresources.presentation.editor.controller

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.usecase.DeleteResourceUseCase
import dev.robdoes.kmpresources.domain.usecase.SaveResourceUseCase
import dev.robdoes.kmpresources.ide.refactoring.KmpResourceRefactorService
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

/**
 * Controller responsible for handling resource table operations within a Kotlin Multiplatform Project.
 *
 * This class provides methods to manage actions related to resources, including deletion, saving, and
 * locale addition, ensuring synchronization with project files and triggering updates where necessary.
 *
 * @constructor Creates an instance of the controller with the specified dependencies.
 * @param project The current project instance.
 * @param file The virtual file associated with the resource table being edited.
 * @param scannerService The service responsible for checking resource usage in the project.
 * @param deleteResourceUseCase Use case for handling resource deletion logic.
 * @param saveResourceUseCase Use case for handling resource saving logic.
 */
internal class KmpResourceTableEditorController(
    private val project: Project,
    private val file: VirtualFile,
    private val scannerService: ResourceUsageService,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val saveResourceUseCase: SaveResourceUseCase
) {
    var onDataChanged: () -> Unit = {}
    var onShowUsagesRequested: (String) -> Unit = {}

    /**
     * Handles the deletion of a sub-item within a resource, such as a plural form or a string
     * in a localized string array. Prompts for user confirmation if the resource type is plural.
     *
     * @param resource The XML resource where the sub-item resides. This includes the type and
     *                 localized values of the resource.
     * @param subItemIdentifier The identifier of the sub-item to be deleted. For example, in a
     *                          plural resource, this might represent a specific quantity string
     *                          like "one" or "many".
     */
    suspend fun handleSubItemDeletion(resource: XmlResource, subItemIdentifier: String) {
        val isPlural = resource.type == ResourceType.Plural
        val proceed = withEdtContext {
            if (isPlural) {
                Messages.showYesNoDialog(
                    project,
                    KmpResourcesBundle.message("dialog.delete.plural.message", subItemIdentifier, resource.key),
                    KmpResourcesBundle.message("dialog.delete.plural.title"),
                    Messages.getQuestionIcon()
                ) == Messages.YES
            } else true
        }

        if (proceed) {
            deleteResourceUseCase(resource.key, resource.type, true, subItemIdentifier)
            withEdtContext {
                onDataChanged()
                KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
            }
        }
    }

    /**
     * Handles the deletion of a main XML resource after checking its usage and providing user confirmations.
     *
     * This method first determines if the specified resource is currently in use within the project.
     * If the resource is not in use, the user is prompted with a confirmation dialog before deletion.
     * If the resource is in use, the user is presented with a warning dialog and an option to view usages.
     *
     * @param resource The XML resource to be deleted. The resource contains details such as its type and key.
     */
    suspend fun handleMainResourceDeletion(resource: XmlResource) {
        val isUsed = scannerService.isResourceUsed(resource.key)

        withEdtContext {
            if (!isUsed) {
                if (Messages.showYesNoDialog(
                        project,
                        KmpResourcesBundle.message("dialog.delete.resource.message", resource.key),
                        KmpResourcesBundle.message("dialog.delete.resource.title"),
                        Messages.getQuestionIcon()
                    ) == Messages.YES
                ) {
                    deleteResourceUseCase(resource.key, resource.type, false)
                    onDataChanged()
                    KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
                }
            } else {
                if (Messages.showDialog(
                        project,
                        KmpResourcesBundle.message("dialog.warning.in_use.message", resource.key),
                        KmpResourcesBundle.message("dialog.warning.in_use.title"),
                        arrayOf(
                            KmpResourcesBundle.message("dialog.btn.show_usages"),
                            KmpResourcesBundle.message("dialog.btn.cancel")
                        ),
                        0,
                        Messages.getWarningIcon()
                    ) == 0
                ) {
                    onShowUsagesRequested(resource.key)
                }
            }
        }
    }

    /**
     * Handles the saving of an XML resource, ensuring that duplicate keys are checked,
     * and necessary refactoring or updates are performed in the module.
     *
     * This method verifies if the resource key conflicts with an existing resource's key
     * and prompts the user with an error message if it does. Additionally, it manages the
     * renaming of resource keys, updates the state of the module, and triggers necessary actions
     * for synchronization and data updates.
     *
     * @param resourceToSave The [XmlResource] to be saved. This includes metadata, localized values,
     *                       and the key for the resource.
     * @param existingResource An optional [XmlResource] representing an existing resource with the
     *                         same or a different key. If null, it indicates a new resource is being added.
     * @param currentEditingOldKey An optional string representing the key currently being edited.
     *                              This is used to handle key renaming when needed.
     * @return A [Boolean] value indicating whether the resource was successfully saved.
     *         Returns `true` if the save operation was successful and no conflicts were detected;
     *         otherwise, returns `false`.
     */
    suspend fun handleResourceSave(
        resourceToSave: XmlResource,
        existingResource: XmlResource?,
        currentEditingOldKey: String?
    ): Boolean {
        val isDuplicate = withEdtContext {
            if (existingResource != null && existingResource.type.xmlTag != resourceToSave.type.xmlTag && currentEditingOldKey != resourceToSave.key) {
                Messages.showErrorDialog(
                    project,
                    KmpResourcesBundle.message("dialog.error.key.exists", resourceToSave.key),
                    KmpResourcesBundle.message("dialog.error.title")
                )
                true
            } else false
        }

        if (isDuplicate) return false

        if (currentEditingOldKey != null && currentEditingOldKey != resourceToSave.key) {
            KmpResourceRefactorService.renameKeyInModule(
                project, file, resourceToSave.type.xmlTag, currentEditingOldKey, resourceToSave.key
            )
        }

        withEdtContext {
            saveResourceUseCase(resourceToSave)
            onDataChanged()
            KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
        }
        return true
    }
}