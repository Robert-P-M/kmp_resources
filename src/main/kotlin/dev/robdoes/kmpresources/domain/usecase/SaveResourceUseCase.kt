package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case responsible for saving an XML-based resource to the repository.
 *
 * This class provides functionality to persist a given [XmlResource] instance
 * by utilizing the [ResourceRepository]. It acts as an intermediary between
 * the application logic and the underlying resource repository for saving resource data.
 *
 * @constructor Creates an instance of [SaveResourceUseCase].
 * @param repository The repository instance used for managing XML resources.
 */
internal class SaveResourceUseCase(private val repository: ResourceRepository) {

    /**
     * Saves the given XML resource to the repository.
     *
     * This operator function is responsible for persisting or updating an [XmlResource] in the associated
     * [ResourceRepository]. Use this method to ensure that the resource data, including localized values
     * and metadata, is stored in the repository for future retrieval or processing.
     *
     * @param resource The [XmlResource] instance to be saved. It includes details such as the type, localized values,
     * and other properties necessary for storing and managing the resource.
     */
    suspend operator fun invoke(resource: XmlResource) {
        repository.saveResource(resource)
    }
}

