package dev.robdoes.kmpresources.presentation.editor.controller

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.usecase.ResourceKeyValidator

class ResourceEditPanelController(private val project: Project) {

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

    fun getHeaderTitle(isUpdate: Boolean, type: String): String {
        return if (!isUpdate) KmpResourcesBundle.message("ui.panel.title.add")
        else when (type) {
            "string" -> KmpResourcesBundle.message("ui.panel.title.edit.string")
            "plurals" -> KmpResourcesBundle.message("ui.panel.title.edit.plural")
            else -> KmpResourcesBundle.message("ui.panel.title.edit.array")
        }
    }

    fun getHeaderDescription(isUpdate: Boolean, type: String): String {
        return if (!isUpdate) KmpResourcesBundle.message("ui.panel.desc.add")
        else when (type) {
            "string" -> KmpResourcesBundle.message("ui.panel.desc.edit.string")
            "plurals" -> KmpResourcesBundle.message("ui.panel.desc.edit.plural")
            else -> KmpResourcesBundle.message("ui.panel.desc.edit.array")
        }
    }

    private fun showError(messageKey: String) {
        Messages.showErrorDialog(
            project,
            KmpResourcesBundle.message(messageKey),
            KmpResourcesBundle.message("dialog.error.title")
        )
    }
}