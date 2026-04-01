package dev.robdoes.kmpresources.presentation.shared


import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.Bcp47FolderMapper
import dev.robdoes.kmpresources.data.repository.XmlLocaleRepositoryFactory
import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase
import java.io.IOException

/**
 * Handles interactions related to locale management within the Kotlin Multiplatform project.
 * Provides functionality to add and remove locales, interacting with services and repositories
 * to manage locale resources.
 *
 * @constructor Initializes the handler with the given project instance.
 * @param project The current project context within which locale operations will be performed.
 */
internal class KmpLocaleInteractionHandler(
    private val project: Project
) {

    /**
     * Adds a new locale to the system by invoking the AddLocaleUseCase, which handles the necessary
     * directory and file creation for the specified locale tag. Displays an error dialog to the user
     * if the addition process fails.
     *
     * @param localeTag The BCP 47 language tag identifying the locale to be added (e.g., "en-US").
     * @return A boolean indicating whether the locale was successfully added (`true`) or if an error occurred (`false`).
     */
    suspend fun addLocale(localeTag: String): Boolean {
        val localeFactory = project.service<XmlLocaleRepositoryFactory>()
        val localeRepo = localeFactory.createLocaleRepository()
        val addLocaleUseCase = AddLocaleUseCase(localeRepository = localeRepo)

        return try {
            addLocaleUseCase(localeTag)
            true
        } catch (e: Exception) {
            withEdtContext {
                Messages.showErrorDialog(
                    project,
                    KmpResourcesBundle.message("dialog.error.add.locale", localeTag, e.localizedMessage ?: ""),
                    KmpResourcesBundle.message("dialog.error.title")
                )
            }
            false
        }
    }

    /**
     * Removes a locale globally by deleting its associated resource folder
     * from the project. Displays a confirmation dialog to the user
     * and provides error feedback on failure.
     *
     * @param localeTag The BCP 47 language tag identifying the locale to be removed (e.g., "en-US").
     * @return A boolean indicating whether the locale was successfully removed (`true`)
     * or if the operation was unsuccessful (`false`).
     */
    suspend fun removeLocaleGlobal(localeTag: String): Boolean {
        val folderName = Bcp47FolderMapper.bcp47ToFolderName(localeTag)

        val proceed = withEdtContext {
            val validator = object : InputValidatorEx {
                override fun checkInput(inputString: String?) = inputString == folderName
                override fun canClose(inputString: String?) = inputString == folderName
                override fun getErrorText(inputString: String?) =
                    if (inputString == folderName) null else KmpResourcesBundle.message("dialog.delete.restrictive.error", folderName)
            }

            val result = Messages.showInputDialog(
                project,
                KmpResourcesBundle.message("dialog.delete.locale.message", folderName),
                KmpResourcesBundle.message("dialog.delete.locale.title"),
                Messages.getWarningIcon(),
                "",
                validator
            )
            result == folderName
        }

        if (!proceed) return false

        val issueService = project.service<ResourceIssueService>()
        val files = issueService.findAllResourceFiles()
        val foldersToDelete = files.mapNotNull { it.parent }.filter { it.name == folderName }.distinct()

        var success = true
        withEdtContext {
            WriteCommandAction.runWriteCommandAction(project) {
                foldersToDelete.forEach { folder ->
                    try {
                        folder.delete(this)
                    } catch (e: IOException) {
                        success = false
                        Messages.showErrorDialog(
                            project,
                            KmpResourcesBundle.message("dialog.error.delete.locale", folderName, e.localizedMessage ?: ""),
                            KmpResourcesBundle.message("dialog.error.title")
                        )
                    }
                }
            }
        }
        return success
    }

    /**
     * Renames a locale across all associated resource folders in the project.
     * If the operation detects a name conflict with an existing folder, it will fail
     * and optionally show an error dialog to the user.
     *
     * @param oldLocaleTag The BCP 47 language tag of the locale to be renamed (e.g., "en-US").
     * @param newLocaleTag The BCP 47 language tag representing the new name for the locale (e.g., "fr-FR").
     * @return A boolean indicating whether the renaming operation was successful (`true`) or if
     * it failed due to errors or naming conflicts (`false`).
     */
    suspend fun renameLocaleGlobal(oldLocaleTag: String, newLocaleTag: String): Boolean {
        val oldFolderName = Bcp47FolderMapper.bcp47ToFolderName(oldLocaleTag)
        val newFolderName = Bcp47FolderMapper.bcp47ToFolderName(newLocaleTag)

        val issueService = project.service<ResourceIssueService>()
        val files = issueService.findAllResourceFiles()
        val foldersToRename = files.mapNotNull { it.parent }.filter { it.name == oldFolderName }.distinct()

        if (foldersToRename.isEmpty()) return false

        val hasConflict = foldersToRename.any { it.parent?.findChild(newFolderName) != null }
        if (hasConflict) {
            withEdtContext {
                Messages.showErrorDialog(
                    project,
                    KmpResourcesBundle.message("dialog.error.rename.conflict", newLocaleTag),
                    KmpResourcesBundle.message("dialog.error.title")
                )
            }
            return false
        }

        var success = true
        withEdtContext {
            WriteCommandAction.runWriteCommandAction(project) {
                foldersToRename.forEach { folder ->
                    try {
                        folder.rename(this, newFolderName)
                    } catch (e: IOException) {
                        success = false
                        Messages.showErrorDialog(
                            project,
                            KmpResourcesBundle.message("dialog.error.rename.locale", oldFolderName, e.localizedMessage ?: ""),
                            KmpResourcesBundle.message("dialog.error.title")
                        )
                    }
                }
            }
        }
        return success
    }
}