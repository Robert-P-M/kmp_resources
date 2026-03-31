package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * A use case for updating or modifying localized string arrays within XML resources.
 *
 * This class is responsible for managing in-line updates to string array resources by
 * adding, editing, or removing elements for a specific locale. It interacts with the
 * resource repository and the resource loading use case to apply changes and persist
 * updates.
 *
 * @constructor Initializes the use case with the required dependencies.
 * @param repository The repository used to save the updated string array resource.
 * @param loadResourcesUseCase The use case responsible for loading the existing XML resources.
 */
internal class UpdateInlineArrayUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    /**
     * Updates or modifies a localized string array resource based on the provided parameters.
     *
     * This function allows for adding, modifying, or removing entries in a localized string array
     * for a specific locale. If the resource with the given key exists, it retrieves the localized
     * items, applies the requested operation at the specified index, and saves the updated resource
     * back to the repository.
     *
     * @param key The unique identifier for the string array resource to update.
     * @param localeTag The BCP 47 language tag representing the locale to update. Can be `null` for the default locale.
     * @param isUntranslatable A flag indicating whether the resource is not meant to be translated across locales.
     * @param index The position in the localized string array to update. Use `-1` to add a new element to the array.
     * @param newValue The new value to set at the specified index, or an empty string to remove the value at the index.
     */
    suspend operator fun invoke(
        key: String,
        localeTag: String?,
        isUntranslatable: Boolean,
        index: Int,
        newValue: String
    ) {
        val existingResources = loadResourcesUseCase()
        val existingArray = existingResources.findResource<StringArrayResource>(key)

        if (existingArray != null) {
            val allLocalizedItems = existingArray.localizedItems.toMutableMap()
            val currentLocaleItems = allLocalizedItems[localeTag]?.toMutableList() ?: mutableListOf()

            if (index == -1 && newValue.isNotBlank()) {
                currentLocaleItems.add(newValue)
            } else if (index in currentLocaleItems.indices) {
                if (newValue.isNotBlank()) {
                    currentLocaleItems[index] = newValue
                } else {
                    currentLocaleItems.removeAt(index)
                }
            }

            allLocalizedItems[localeTag] = currentLocaleItems

            repository.saveResource(
                StringArrayResource(key, isUntranslatable, allLocalizedItems)
            )
        }
    }
}