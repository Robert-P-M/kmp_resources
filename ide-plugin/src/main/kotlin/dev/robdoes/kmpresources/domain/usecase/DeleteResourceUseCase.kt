package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case for deleting resources or sub-items of resources.
 *
 * This use case handles the deletion of resources identified by a unique key
 * and type from the repository. It also supports the deletion of specific
 * sub-items within a resource, based on additional criteria such as sub-item
 * identifiers.
 *
 * @property repository The repository managing the resources to be deleted.
 * @property loadResourcesUseCase Use case for loading available resources,
 * used for accessing data required during sub-item deletion operations.
 */
internal class DeleteResourceUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {
    /**
     * Deletes a resource or a specific sub-item of a resource based on the provided parameters.
     *
     * This function enables the deletion of resources from the repository. If `isSubItem` is true, it will delete a sub-item
     * identified by `subItemIdentifier` using a specialized handler. Otherwise, it will delete the main resource specified
     * by the `key` and `resourceType`.
     *
     * @param key The unique identifier of the resource to be deleted.
     * @param resourceType The type of resource to be deleted, represented by the [ResourceType] interface.
     * @param isSubItem A flag indicating whether the target of the deletion is a sub-item of the resource.
     * @param subItemIdentifier An optional identifier for the sub-item to be deleted. Defaults to an empty string if
     *                          the deletion target is not a sub-item.
     */
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

    /**
     * Handles the deletion of specific sub-items from a resource identified by its key.
     *
     * This method processes and deletes sub-items from resources such as `StringArrayResource` or
     * `PluralsResource` based on the provided key and sub-item identifier. Depending on the resource type,
     * it updates the resource and persists the changes. Does nothing for `StringResource`.
     *
     * @param key The unique identifier of the resource that contains the sub-item to delete.
     * @param subItemIdentifier A string representing the specific sub-item to be removed.
     * For `StringArrayResource`, it identifies an index in the format `item[index]`. For `PluralsResource`,
     * it identifies a quantity category key (e.g., "one").
     */
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