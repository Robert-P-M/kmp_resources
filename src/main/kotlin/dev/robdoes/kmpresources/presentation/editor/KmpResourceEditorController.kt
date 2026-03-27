package dev.robdoes.kmpresources.presentation.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.data.repository.XmlLocaleRepositoryFactory
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.usecase.DeleteResourceUseCase
import dev.robdoes.kmpresources.domain.usecase.SaveResourceUseCase
import dev.robdoes.kmpresources.ide.refactoring.KmpResourceRefactorService
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper

class KmpResourceEditorController(
    private val project: Project,
    private val file: VirtualFile,
    private val scannerService: ResourceUsageService,
    private val deleteResourceUseCase: DeleteResourceUseCase,
    private val saveResourceUseCase: SaveResourceUseCase
) {
    var onDataChanged: () -> Unit = {}
    var onShowUsagesRequested: (String) -> Unit = {}

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

    suspend fun handleAddLocale(localeTag: String) {
        val localeFactory = project.service<XmlLocaleRepositoryFactory>()
        val localeRepo = localeFactory.createLocaleRepository()
        val addLocaleUseCase = dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase(
            localeRepository = localeRepo,
            resourceRepositoryFactory = localeFactory.resourceRepositoryFactory()
        )

        try {
            addLocaleUseCase(localeTag)
            withEdtContext {
                onDataChanged()
            }
        } catch (e: Exception) {
            withEdtContext {
                Messages.showErrorDialog(
                    project,
                    e.message ?: "Failed to add locale $localeTag",
                    KmpResourcesBundle.message("dialog.error.title")
                )
            }
        }
    }
}