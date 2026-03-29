package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class DeleteResourceUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    suspend operator fun invoke(
        key: String,
        resourceType: ResourceType,
        isSubItem: Boolean,
        subItemIdentifier: String = ""
    ) {
        if (isSubItem) {
            handleSubItemDeletion(key, subItemIdentifier)
        } else {
            repository.deleteResource(key, resourceType)
        }
    }

    private suspend fun handleSubItemDeletion(key: String, subItemIdentifier: String) {
        val existingResource = loadResourcesUseCase().find { it.key == key } ?: return

        when (existingResource) {
            is StringArrayResource -> {
                if (subItemIdentifier.startsWith("item[")) {
                    val index = subItemIdentifier.substringAfter("[").substringBefore("]").toIntOrNull() ?: return

                    val updatedLocalizedItems = existingResource.localizedItems.mapValues { (_, items) ->
                        items.toMutableList().apply { if (index in indices) removeAt(index) }
                    }

                    repository.saveResource(existingResource.copy(localizedItems = updatedLocalizedItems))
                }
            }

            is PluralsResource -> {
                val updatedLocalizedItems = existingResource.localizedItems.mapValues { (_, items) ->
                    items.toMutableMap().apply { remove(subItemIdentifier) }
                }

                repository.saveResource(existingResource.copy(localizedItems = updatedLocalizedItems))
            }

            is StringResource -> {}
        }
    }
}