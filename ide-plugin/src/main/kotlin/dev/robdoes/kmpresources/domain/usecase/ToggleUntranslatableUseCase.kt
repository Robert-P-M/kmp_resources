package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case responsible for toggling the untranslatable status of a specific resource in the repository.
 *
 * This class provides functionality to modify the metadata of a resource to indicate whether it should
 * be excluded from translation workflows. Resources marked as untranslatable are ignored during translation,
 * allowing developers to manage which resources remain in their original language.
 *
 * @constructor Initializes the [ToggleUntranslatableUseCase] with the provided [ResourceRepository].
 * @param repository The repository instance used for managing XML resources and their metadata.
 */
internal class ToggleUntranslatableUseCase(private val repository: ResourceRepository) {

    /**
     * Toggles the untranslatable status of a resource identified by the given key.
     *
     * This function updates the specified resource's metadata in the repository, determining whether
     * it should be excluded from translation workflows. Resources marked as untranslatable remain
     * in their original language and are not processed for localization.
     *
     * @param key The unique identifier of the resource whose untranslatable status will be toggled.
     * @param isUntranslatable A boolean indicating the desired untranslatable status. Set to `true`
     *                         to mark the resource as untranslatable, or `false` to allow translation.
     */
    suspend operator fun invoke(key: String, isUntranslatable: Boolean) {
        repository.toggleUntranslatable(key, isUntranslatable)
    }
}
