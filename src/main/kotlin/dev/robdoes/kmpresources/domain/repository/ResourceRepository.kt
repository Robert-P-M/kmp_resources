package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.model.XmlResource

interface ResourceRepository {
    fun loadResources(): List<XmlResource>
    fun saveResource(resource: XmlResource)
    fun deleteResource(key: String, xmlTag: String)
    fun toggleUntranslatable(key: String, isUntranslatable: Boolean)
}