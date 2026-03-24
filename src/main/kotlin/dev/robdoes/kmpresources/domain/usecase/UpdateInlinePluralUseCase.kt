package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlinePluralUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    operator fun invoke(key: String, isUntranslatable: Boolean, quantity: String, newValue: String) {
        val existingPlural = loadResourcesUseCase().find { it.key == key && it is PluralsResource } as? PluralsResource
        if (existingPlural != null) {
            val updatedItems = existingPlural.items.toMutableMap()
            if (newValue.isNotBlank()) {
                updatedItems[quantity] = newValue
            } else {
                updatedItems.remove(quantity)
            }
            repository.saveResource(PluralsResource(key, isUntranslatable, updatedItems))
        }
    }
}