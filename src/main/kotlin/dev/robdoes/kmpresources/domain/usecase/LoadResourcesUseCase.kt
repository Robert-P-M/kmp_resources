package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class LoadResourcesUseCase(private val repository: ResourceRepository) {
    operator fun invoke(): List<XmlResource> {
        return repository.loadResources()
    }
}