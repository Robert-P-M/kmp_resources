package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class ToggleUntranslatableUseCase(private val repository: ResourceRepository) {
    suspend operator fun invoke(key: String, isUntranslatable: Boolean) {
        repository.toggleUntranslatable(key, isUntranslatable)
    }
}
