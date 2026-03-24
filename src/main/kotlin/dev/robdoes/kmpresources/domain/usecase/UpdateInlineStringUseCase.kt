package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlineStringUseCase(private val repository: ResourceRepository) {
    operator fun invoke(key: String, isUntranslatable: Boolean, newValue: String) {
        repository.saveResource(StringResource(key, isUntranslatable, newValue))
    }
}