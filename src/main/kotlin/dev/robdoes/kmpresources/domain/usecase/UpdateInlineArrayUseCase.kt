package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlineArrayUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    suspend operator fun invoke(key: String, localeTag: String?, isUntranslatable: Boolean, index: Int, newValue: String) {
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