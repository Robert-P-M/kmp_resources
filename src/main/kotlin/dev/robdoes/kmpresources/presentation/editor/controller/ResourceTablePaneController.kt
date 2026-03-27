package dev.robdoes.kmpresources.presentation.editor.controller

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.presentation.editor.model.ResourceStatus
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResourceTablePaneController(
    private val project: Project,
    private val scannerService: ResourceUsageService
) {
    fun validateResources(
        resources: List<XmlResource>,
        locales: List<LocaleInfo>,
        onStatusUpdated: (key: String, status: ResourceStatus, subStatuses: Map<String, ResourceStatus?>) -> Unit
    ) {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            DumbService.getInstance(project).waitForSmartMode()

            resources.forEach { res ->
                val isUsed = scannerService.isResourceUsed(res.key)
                val missingTranslation = isMissingAnyTranslation(res, locales)

                val mainStatus = determineStatus(isUsed, missingTranslation)
                val subStatuses = mutableMapOf<String, ResourceStatus?>()

                when (res) {
                    is PluralsResource -> {
                        val defaultKeys = res.localizedItems[null]?.keys ?: emptySet()
                        res.type.supportedQuantities.forEach { q ->
                            if (q !in defaultKeys) {
                                subStatuses[q] = null
                            } else {
                                val isMissing = !res.isUntranslatable && locales.any {
                                    res.localizedItems[it.languageTag]?.get(q).isNullOrBlank()
                                }
                                subStatuses[q] = determineStatus(isUsed, isMissing)
                            }
                        }
                    }
                    is StringArrayResource -> {
                        val defaultSize = res.localizedItems[null]?.size ?: 0
                        val maxItems = locales.maxOfOrNull { res.localizedItems[it.languageTag]?.size ?: 0 } ?: 0
                        for (i in 0 until maxOf(maxItems, defaultSize)) {
                            if (i >= defaultSize) {
                                subStatuses["item[$i]"] = null
                            } else {
                                val isMissing = !res.isUntranslatable && locales.any { l ->
                                    val items = res.localizedItems[l.languageTag] ?: emptyList()
                                    i >= items.size || items[i].isBlank()
                                }
                                subStatuses["item[$i]"] = determineStatus(isUsed, isMissing)
                            }
                        }
                    }
                    else -> {}
                }

                withContext(Dispatchers.EDT) {
                    onStatusUpdated(res.key, mainStatus, subStatuses)
                }
            }
        }
    }

    private fun determineStatus(isUsed: Boolean, isMissing: Boolean): ResourceStatus {
        return when {
            !isUsed -> ResourceStatus(AllIcons.General.Error, KmpResourcesBundle.message("ui.table.status.tooltip.unused"))
            isMissing -> ResourceStatus(AllIcons.General.Warning, KmpResourcesBundle.message("ui.table.status.tooltip.missing_translation"))
            else -> ResourceStatus(AllIcons.General.InspectionsOK, KmpResourcesBundle.message("ui.table.status.tooltip.ok"))
        }
    }

    private fun isMissingAnyTranslation(res: XmlResource, locales: List<LocaleInfo>): Boolean {
        if (res.isUntranslatable) return false
        return locales.any { locale ->
            val tag = locale.languageTag
            when (res) {
                is StringResource -> res.values[tag].isNullOrBlank()
                is PluralsResource -> {
                    val defaultKeys = res.localizedItems[null]?.keys ?: emptySet()
                    val localeItems = res.localizedItems[tag] ?: emptyMap()
                    defaultKeys.any { localeItems[it].isNullOrBlank() }
                }
                is StringArrayResource -> {
                    val defaultSize = res.localizedItems[null]?.size ?: 0
                    val localeItems = res.localizedItems[tag] ?: emptyList()
                    localeItems.size < defaultSize || localeItems.any { it.isBlank() }
                }
            }
        }
    }
}