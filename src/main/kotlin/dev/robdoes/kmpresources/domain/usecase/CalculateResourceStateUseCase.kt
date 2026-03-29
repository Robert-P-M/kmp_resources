package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.*

class CalculateResourceStateUseCase(private val scannerService: ResourceUsageService) {

    suspend operator fun invoke(res: XmlResource, locales: List<LocaleInfo>): ResourceEvaluation {
        val isUsed = scannerService.isResourceUsed(res.key)
        val isMissingMain = !res.isUntranslatable && locales.any { res.isEmptyForLocale(it.languageTag) }

        val mainState = determineState(isUsed, isMissingMain)
        val subStates = mutableMapOf<String, ResourceState?>()

        when (res) {
            is PluralsResource -> {
                val defaultKeys = res.localizedItems[null]?.keys ?: emptySet()
                res.type.supportedQuantities.forEach { q ->
                    if (q !in defaultKeys) {
                        subStates[q] = null
                    } else {
                        val isMissingSub = !res.isUntranslatable && locales.any {
                            res.localizedItems[it.languageTag]?.get(q).isNullOrBlank()
                        }
                        subStates[q] = determineState(isUsed, isMissingSub)
                    }
                }
            }

            is StringArrayResource -> {
                val defaultSize = res.localizedItems[null]?.size ?: 0
                val maxItems = locales.maxOfOrNull { res.localizedItems[it.languageTag]?.size ?: 0 } ?: 0
                for (i in 0 until maxOf(maxItems, defaultSize)) {
                    if (i >= defaultSize) {
                        subStates["item[$i]"] = null
                    } else {
                        val isMissingSub = !res.isUntranslatable && locales.any { l ->
                            val items = res.localizedItems[l.languageTag] ?: emptyList()
                            i >= items.size || items[i].isBlank()
                        }
                        subStates["item[$i]"] = determineState(isUsed, isMissingSub)
                    }
                }
            }

            else -> {}
        }

        return ResourceEvaluation(mainState, subStates)
    }

    private fun determineState(isUsed: Boolean, isMissing: Boolean): ResourceState {
        return when {
            !isUsed -> ResourceState.UNUSED
            isMissing -> ResourceState.MISSING_TRANSLATION
            else -> ResourceState.OK
        }
    }
}