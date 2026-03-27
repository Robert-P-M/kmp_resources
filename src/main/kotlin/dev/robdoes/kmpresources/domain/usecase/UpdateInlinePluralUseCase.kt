package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlinePluralUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    operator fun invoke(
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