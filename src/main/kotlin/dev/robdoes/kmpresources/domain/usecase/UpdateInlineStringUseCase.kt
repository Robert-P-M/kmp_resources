package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.findResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class UpdateInlineStringUseCase(
    private val repository: ResourceRepository,
    private val loadResourcesUseCase: LoadResourcesUseCase
) {

    suspend operator fun invoke(key: String, localeTag: String?, isUntranslatable: Boolean, newValue: String) {
        val existingResources = loadResourcesUseCase()
        val existingString = existingResources.findResource<StringResource>(key)

        if (existingString != null) {
            val updatedValues = existingString.values.toMutableMap()
            updatedValues[localeTag] = newValue

            repository.saveResource(
                StringResource(
                    key = key,
                    isUntranslatable = isUntranslatable,
                    values = updatedValues
                )
            )
        }
    }
}