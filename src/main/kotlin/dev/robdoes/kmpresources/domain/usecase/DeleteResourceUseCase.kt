package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class DeleteResourceUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    /**
     * @param key The resource key
     * @param type The type (e.g., "string", "item[1]", "other")
     * @param isSubItem True if we are deleting an item inside an array or plural
     */
    operator fun invoke(key: String, type: String, isSubItem: Boolean) {
        if (isSubItem) {
            handleSubItemDeletion(key, type)
        } else {
            repository.deleteResource(key, type)
        }
    }

    private fun handleSubItemDeletion(key: String, type: String) {
        if (type.startsWith("item[")) {
            val indexStr = type.substringAfter("[").substringBefore("]")
            if (indexStr != "+") {
                val index = indexStr.toIntOrNull() ?: -1
                val existingArray = loadResourcesUseCase().findResource<StringArrayResource>(key)

                if (existingArray != null && index in existingArray.items.indices) {
                    val updatedItems = existingArray.items.toMutableList().apply { removeAt(index) }
                    repository.saveResource(
                        StringArrayResource(
                            key,
                            existingArray.isUntranslatable,
                            updatedItems
                        )
                    )
                }
            }
        } else {
            val existingPlural = loadResourcesUseCase().findResource<PluralsResource>(key)

            if (existingPlural != null) {
                val updatedItems = existingPlural.items.toMutableMap().apply { remove(type) }
                repository.saveResource(PluralsResource(key, existingPlural.isUntranslatable, updatedItems))
            }
        }
    }
}
