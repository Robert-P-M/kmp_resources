package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

/**
 * Use case responsible for loading all available XML resources from the repository.
 *
 * This class serves as an abstraction layer for retrieving XML-based resources managed
 * by the [ResourceRepository]. These resources are typically defined using the
 * [XmlResource] interface and include various types of resources, such as strings,
 * plurals, and string arrays.
 *
 * @constructor Initializes a new instance of the use case with the provided repository.
 * @param repository The [ResourceRepository] instance used to load the XML resources.
 */
internal class LoadResourcesUseCase(private val repository: ResourceRepository) {
    /**
     * Invokes the use case to load all available XML resources from the repository.
     *
     * This operator function fetches a list of resources adhering to the [XmlResource] interface,
     * such as strings, plurals, and string arrays. The resources are typically retrieved from
     * the underlying [ResourceRepository].
     *
     * @return A list of [XmlResource] objects representing the XML-based resources available
     *         in the repository.
     */
    operator fun invoke(): List<XmlResource> {
        return repository.loadResources()
    }
}