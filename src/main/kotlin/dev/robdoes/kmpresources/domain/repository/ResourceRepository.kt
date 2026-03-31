package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.XmlResource

/**
 * Provides an interface for managing XML-based resources within the application.
 *
 * This repository interface defines methods for loading, parsing, saving, and managing
 * XML resources that are used in Compose-based applications. The resources represented
 * by this interface adhere to the structure defined by [XmlResource].
 */
internal interface ResourceRepository {

    /**
     * Loads a list of all available XML resources managed by the repository.
     *
     * This method retrieves the resources stored within the repository, adhering to the
     * structure defined by [XmlResource]. The returned list contains XML-based resources
     * such as strings, plurals, or string arrays used within the system.
     *
     * @return A list of [XmlResource] objects representing the loaded XML resources.
     */
    fun loadResources(): List<XmlResource>

    /**
     * Parses and retrieves a list of XML resources from disk storage.
     *
     * This method asynchronously reads and parses XML-based resources from
     * the file system, transforming them into a structured list of [XmlResource]
     * objects. These resources can include various types such as strings, plurals,
     * and string arrays, adhering to the defined structure of [XmlResource].
     *
     * @return A list of [XmlResource] objects parsed from disk.
     */
    suspend fun parseResourcesFromDisk(): List<XmlResource>

    /**
     * Saves the provided XML resource to the repository.
     *
     * This method allows storing or updating an XML-based resource represented by the [XmlResource]
     * interface in the underlying repository. Use this function to persist changes made to
     * localized values, resource type, or any other attributes of the resource.
     *
     * @param resource The [XmlResource] instance to be saved. It contains the resource's type,
     * localized values, and metadata necessary for storing the resource in the repository.
     */
    suspend fun saveResource(resource: XmlResource)

    /**
     * Deletes a resource from the repository based on the specified key and type.
     *
     * This method removes an existing resource identified by its unique key and type.
     * It is meant to manage cleanup or removal of resources that are no longer needed.
     *
     * @param key The unique identifier of the resource to be deleted.
     * @param type The type of the resource to be deleted, represented by [ResourceType].
     */
    suspend fun deleteResource(key: String, type: ResourceType)

    /**
     * Toggles the untranslatable status of a resource identified by the provided key.
     *
     * This method updates the metadata of a resource, specified by its unique key,
     * within the repository to indicate whether or not it should be considered
     * untranslatable. Resources marked as untranslatable are excluded from translation workflows.
     *
     * @param key The unique identifier of the resource whose untranslatable status is being modified.
     * @param isUntranslatable A boolean indicating the new untranslatable status of the resource.
     *                         `true` marks the resource as untranslatable, while `false` marks it as translatable.
     */
    suspend fun toggleUntranslatable(key: String, isUntranslatable: Boolean)
}