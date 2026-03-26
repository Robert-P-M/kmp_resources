package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlineArrayUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    operator fun invoke(key: String, isUntranslatable: Boolean, index: Int, newValue: String) {
        val existingArray =
            loadResourcesUseCase().findResource<StringArrayResource>(key)
        if (existingArray != null) {
            val updatedItems = existingArray.items.toMutableList()
            if (index == -1 && newValue.isNotBlank()) {
                updatedItems.add(newValue)
            } else if (index in updatedItems.indices) {
                if (newValue.isNotBlank()) {
                    updatedItems[index] = newValue
                } else {
                    updatedItems.removeAt(index)
                }
            }
            repository.saveResource(StringArrayResource(key, isUntranslatable, updatedItems))
        }
    }
}