package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.XmlResource

interface ResourceRepository {
    fun loadResources(): List<XmlResource>
    suspend fun parseResourcesFromDisk(): List<XmlResource>
    suspend fun saveResource(resource: XmlResource)
    suspend fun deleteResource(key: String, type: ResourceType)
    suspend fun toggleUntranslatable(key: String, isUntranslatable: Boolean)
}