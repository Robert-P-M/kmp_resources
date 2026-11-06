package dev.robdoes.kmpresources.presentation.editor.controller

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.usecase.ResourceKeyValidator

/**
 * Controller responsible for managing the resource edit panel.
 *
 * This class provides functionality to handle the creation and modification of XML-based
 * resources such as strings, plurals, and string arrays within the resource editor. It also
 * facilitates validation of resource keys and displays error messages as needed.
 *
 * @constructor Initializes the controller with the specified project instance.
 * @param project The project associated with this controller.
 */
internal class ResourceEditPanelController(private val project: Project) {

    /**
     * Builds an XML resource based on the provided input parameters.
     *
     * This method creates an instance of a specific type of `XmlResource`, such as `StringResource`,
     * `PluralsResource`, or `StringArrayResource`, based on the `type` parameter. It validates the
     * resource key and handles empty or invalid keys by showing an error message and returning `null`.
     * The method also filters out blank entries in `pluralValues` and `arrayValues`, ensuring only
     * non-blank data is included in the resultant resource.
     *
     */
    fun buildResourceFromInput(
        key: String,
        type: String,
        isUntranslatable: Boolean,
        stringValue: String,
        pluralValues: Map<String, String>,
        arrayValues: List<String>
    ): XmlResource? {
        if (key.isBlank()) {
            showError("dialog.error.empty.key")
            return null
        }

        if (!ResourceKeyValidator.isValid(key)) {
            showError("dialog.error.validation.key")
            return null
        }

        return when (type) {
            "string" -> StringResource(key, isUntranslatable, mapOf(null to stringValue))
            "plurals" -> {
                val items = pluralValues.filterValues { it.isNotBlank() }
                PluralsResource(key, isUntranslatable, mapOf(null to items))
            }

            "string-array" -> {
                val items = arrayValues.filter { it.isNotBlank() }
                StringArrayResource(key, isUntranslatable, mapOf(null to items))
            }

            else -> null
        }
    }

    /**
     * Returns the header title for a resource editing panel based on the update status and resource type.
     *
     * @param isUpdate Indicates whether the operation is an update (true) or a creation (false).
     * @param type The type of the resource being edited, such as "string", "plurals", or "array".
     * @return A string representing the header title for the resource editing panel.
     */
    fun getHeaderTitle(isUpdate: Boolean, type: String): String {
        return if (!isUpdate) KmpResourcesBundle.message("ui.panel.title.add")
        else when (type) {
            "string" -> KmpResourcesBundle.message("ui.panel.title.edit.string")
            "plurals" -> KmpResourcesBundle.message("ui.panel.title.edit.plural")
            else -> KmpResourcesBundle.message("ui.panel.title.edit.array")
        }
    }

    /**
     * Generates a description for the header section of a resource editing panel
     * based on the update status and resource type.
     *
     * @param isUpdate Indicates whether the operation is an update (true) or a creation (false).
     * @param type The type of the resource being edited, such as "string", "plurals", or "array".
     * @return A string representing the header description for the resource editing panel.
     */
    fun getHeaderDescription(isUpdate: Boolean, type: String): String {
        return if (!isUpdate) KmpResourcesBundle.message("ui.panel.desc.add")
        else when (type) {
            "string" -> KmpResourcesBundle.message("ui.panel.desc.edit.string")
            "plurals" -> KmpResourcesBundle.message("ui.panel.desc.edit.plural")
            else -> KmpResourcesBundle.message("ui.panel.desc.edit.array")
        }
    }

    /**
     * Displays an error dialog with a specified message.
     *
     * This method uses the provided message key to retrieve a localized
     * error message and displays it as a dialog. It is used to inform
     * users about errors encountered during operations.
     *
     * @param messageKey The key for retrieving the localized error message from the resource bundle.
     */
    private fun showError(messageKey: String) {
        Messages.showErrorDialog(
            project,
            KmpResourcesBundle.message(messageKey),
            KmpResourcesBundle.message("dialog.error.title")
        )
    }
}