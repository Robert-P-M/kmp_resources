package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class DeleteResourceUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    operator fun invoke(key: String, type: String, isSubItem: Boolean) {
        if (isSubItem) {
            handleSubItemDeletion(key, type)
        } else {
            repository.deleteResource(key, type)
        }
    }

    private fun handleSubItemDeletion(key: String, type: String) {
        val existingResource = loadResourcesUseCase().find { it.key == key } ?: return

        when (existingResource) {
            is StringArrayResource -> {
                if (type.startsWith("item[")) {
                    val index = type.substringAfter("[").substringBefore("]").toIntOrNull() ?: return

                    val updatedLocalizedItems = existingResource.localizedItems.mapValues { (_, items) ->
                        items.toMutableList().apply { if (index in indices) removeAt(index) }
                    }

                    repository.saveResource(existingResource.copy(localizedItems = updatedLocalizedItems))
                }
            }

            is PluralsResource -> {
                val updatedLocalizedItems = existingResource.localizedItems.mapValues { (_, items) ->
                    items.toMutableMap().apply { remove(type) }
                }

                repository.saveResource(existingResource.copy(localizedItems = updatedLocalizedItems))
            }

            is StringResource -> {}
        }
    }
}