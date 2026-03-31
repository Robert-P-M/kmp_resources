package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.model.*

internal class FakeResourceRepository : ResourceRepository {

    val inMemoryStorage = mutableListOf<XmlResource>()

    override fun loadResources(): List<XmlResource> {
        return inMemoryStorage.toList()
    }

    override suspend fun parseResourcesFromDisk(): List<XmlResource> {
        return inMemoryStorage.toList()
    }

    override suspend fun saveResource(resource: XmlResource) {
        val existingIndex = inMemoryStorage.indexOfFirst { it.key == resource.key && it.type == resource.type }

        if (existingIndex != -1) {
            val existing = inMemoryStorage[existingIndex]
            // Merge maps to simulate the real repository behavior across locales
            val merged = when (existing) {
                is StringResource -> existing.copy(values = existing.values + (resource as StringResource).values)
                is PluralsResource -> existing.copy(localizedItems = existing.localizedItems + (resource as PluralsResource).localizedItems)
                is StringArrayResource -> existing.copy(localizedItems = existing.localizedItems + (resource as StringArrayResource).localizedItems)
            }
            inMemoryStorage[existingIndex] = merged
        } else {
            inMemoryStorage.add(resource)
        }
    }

    override suspend fun deleteResource(key: String, type: ResourceType) {
        inMemoryStorage.removeIf { it.key == key && it.type == type }
    }

    override suspend fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        val existingIndex = inMemoryStorage.indexOfFirst { it.key == key }
        if (existingIndex == -1) return

        val updatedResource = when (val existing = inMemoryStorage[existingIndex]) {
            is StringResource -> existing.copy(isUntranslatable = isUntranslatable)
            is PluralsResource -> existing.copy(isUntranslatable = isUntranslatable)
            is StringArrayResource -> existing.copy(isUntranslatable = isUntranslatable)
        }

        inMemoryStorage[existingIndex] = updatedResource
    }
}