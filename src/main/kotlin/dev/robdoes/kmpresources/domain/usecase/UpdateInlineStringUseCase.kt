package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case responsible for updating an inline string resource in the repository.
 *
 * This class provides functionality to modify the values of string resources stored
 * in the repository. It allows updating or adding a localized value for a specific
 * locale tag, while optionally marking the resource as untranslatable.
 *
 * @constructor Initializes the use case with the necessary dependencies.
 * @param repository The [ResourceRepository] instance used to persist the updated string resource.
 * @param loadResourcesUseCase The [LoadResourcesUseCase] used to retrieve the existing resources.
 */
internal class UpdateInlineStringUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    /**
     * Updates the value of an existing string resource or adds a new localized value to it in the repository.
     *
     * The method attempts to find a string resource corresponding to the provided key. If found, it updates the
     * specified locale tag's value with the new value and optionally marks the resource as untranslatable. The
     * updated string resource is then saved to the repository.
     *
     * @param key The unique identifier for the string resource to update.
     * @param localeTag The locale tag to update the value for (e.g., "en-US"). If `null`, the value is considered applicable for all locales.
     * @param isUntranslatable Indicates whether the string resource should be marked as untranslatable.
     * @param newValue The new value to set for the specified locale tag.
     */
    suspend operator fun invoke(key: String, localeTag: String?, isUntranslatable: Boolean, newValue: String) {
        val existingResources = loadResourcesUseCase()
        val existingString = existingResources.findResource<StringResource>(key)

        if (existingString != null) {
            val updatedValues = existingString.values.toMutableMap()
            updatedValues[localeTag] = newValue

            repository.saveResource(
                StringResource(
                    key = key,
                    isUntranslatable = isUntranslatable,
                    values = updatedValues
                )
            )
        }
    }
}