package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class SaveResourceUseCase(private val repository: ResourceRepository) {
    suspend operator fun invoke(resource: XmlResource) {
        repository.saveResource(resource)
    }
}

