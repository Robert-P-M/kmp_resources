package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.*

/**
 * Use case responsible for calculating the state of a given XML resource based on its usage
 * and localization information.
 *
 * This class evaluates whether a resource is actively used, whether it has missing translations
 * for its default or localized values, and determines the overall state of the resource and
 * its sub-resources (if applicable).
 *
 * @constructor Initializes the use case with the provided [ResourceUsageService] to determine
 * resource usage in the project context.
 *
 * @param scannerService An instance of [ResourceUsageService] used to check if a resource key
 * is actively used within the project.
 *
 * Functions:
 * @function invoke Evaluates the state of a given resource (`res`) for the specified locales
 * by determining whether the resource is used, whether it has any missing translations,
 * and calculating states for sub-resources (e.g., quantities in plurals, items in arrays).
 * @param res The XML resource to be evaluated.
 * @param locales A list of [LocaleInfo] objects representing the locales to check for missing
 * translations.
 * @return An instance of [ResourceEvaluation], containing the overall state of the resource
 * and the states of its sub-resources.
 */
internal class CalculateResourceStateUseCase(private val scannerService: ResourceUsageService) {

    /**
     * Evaluates the state of a provided resource (`XmlResource`) in the context of multiple locales.
     *
     * This method determines the overall state of the resource (`mainState`) based on its usage
     * and whether it has missing translations. It also assesses the states of sub-resources
     * (`subStates`) for specific types of resources like plurals and string arrays.
     *
     * If the resource is a `PluralsResource`, the sub-states represent the state of each plural quantity.
     * If it is a `StringArrayResource`, the sub-states account for each item in the array.
     *
     * @param res The XML-based resource to evaluate.
     * @param locales A list of locales (`LocaleInfo`) used to assess the missing translation states.
     * @return A `ResourceEvaluation` object containing the overall state of the resource and a map
     *         of sub-resource states identified by their keys or indices.
     */
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

    /**
     * Determines the state of a resource based on its usage and translation availability.
     *
     * @param isUsed A boolean indicating whether the resource is being utilized in the system.
     * @param isMissing A boolean indicating whether the resource is missing necessary translations.
     * @return A `ResourceState` representing the evaluated state of the resource:
     *         - `ResourceState.UNUSED` if the resource is not used.
     *         - `ResourceState.MISSING_TRANSLATION` if the resource is used but has missing translations.
     *         - `ResourceState.OK` if the resource is used and has all required translations.
     */
    private fun determineState(isUsed: Boolean, isMissing: Boolean): ResourceState {
        return when {
            !isUsed -> ResourceState.UNUSED
            isMissing -> ResourceState.MISSING_TRANSLATION
            else -> ResourceState.OK
        }
    }
}