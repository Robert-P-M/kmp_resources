package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case responsible for updating the localized values of a plural resource.
 *
 * This class manages updating existing plural resources with new or updated localized values
 * for a specific quantity and locale. It interacts with a [ResourceRepository] to persist
 * changes and utilizes [LoadResourcesUseCase] to retrieve the current resources.
 *
 * A plural resource is identified by its unique key and may have different localized
 * values for various quantities (e.g., "one", "few", "many") across multiple locales.
 * This use case ensures that changes to a specific quantity and locale are appropriately
 * applied and saved.
 *
 * @constructor Creates an instance of the use case with the specified dependencies.
 * @param repository The [ResourceRepository] interface for saving and managing resources.
 * @param loadResourcesUseCase The [LoadResourcesUseCase] for retrieving current resources.
 */
internal class UpdateInlinePluralUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    /**
     * Updates or removes a localized value for a plural resource based on the specified parameters.
     *
     * This operator function modifies a `PluralsResource` by updating its localized items map
     * according to the given locale tag, quantity, and new value. If the new value is blank,
     * the corresponding quantity entry will be removed. The modified resource is then saved
     * to the repository.
     *
     * @param key The unique identifier of the plural resource to be updated.
     * @param localeTag The locale tag (e.g., "en", "fr") indicating the language and region for the update,
     * or `null` for default values.
     * @param isUntranslatable A flag indicating whether the resource should be excluded from translation.
     * @param quantity The quantity category (e.g., "one", "few", "many") to be updated.
     * @param newValue The new localized string value for the specified quantity. If blank, the entry will be removed.
     */
    suspend operator fun invoke(
        key: String,
        localeTag: String?,
        isUntranslatable: Boolean,
        quantity: String,
        newValue: String
    ) {
        val existingResources = loadResourcesUseCase()
        val existingPlural = existingResources.findResource<PluralsResource>(key)

        if (existingPlural != null) {
            val allLocalizedItems = existingPlural.localizedItems.toMutableMap()

            val currentLocaleItems = allLocalizedItems[localeTag]?.toMutableMap() ?: mutableMapOf()

            if (newValue.isNotBlank()) {
                currentLocaleItems[quantity] = newValue
            } else {
                currentLocaleItems.remove(quantity)
            }

            allLocalizedItems[localeTag] = currentLocaleItems

            repository.saveResource(
                PluralsResource(key, isUntranslatable, allLocalizedItems)
            )
        }
    }
}