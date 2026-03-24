package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.model.XmlResource

/**
 * An in-memory fake for blazing fast unit testing of use cases.
 * No IntelliJ dependencies (VirtualFile, XmlTag, etc.) are needed here!
 */
class FakeResourceRepository : ResourceRepository {

    val inMemoryStorage = mutableListOf<XmlResource>()

    override fun loadResources(): List<XmlResource> {
        return inMemoryStorage.toList()
    }

    override fun saveResource(resource: XmlResource) {
        // If an element with the same key and tag already exists, replace it (update behavior)
        inMemoryStorage.removeIf { it.key == resource.key && it.xmlTag == resource.xmlTag }
        inMemoryStorage.add(resource)
    }

    override fun deleteResource(key: String, xmlTag: String) {
        inMemoryStorage.removeIf { it.key == key && it.xmlTag == xmlTag }
    }

    override fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        val existing = inMemoryStorage.find { it.key == key } ?: return
        inMemoryStorage.remove(existing)

        val updatedResource = when (existing) {
            is dev.robdoes.kmpresources.domain.model.StringResource -> existing.copy(isUntranslatable = isUntranslatable)
            is dev.robdoes.kmpresources.domain.model.PluralsResource -> existing.copy(isUntranslatable = isUntranslatable)
            is dev.robdoes.kmpresources.domain.model.StringArrayResource -> existing.copy(isUntranslatable = isUntranslatable)
        }

        inMemoryStorage.add(updatedResource)
    }
}