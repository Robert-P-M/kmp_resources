package dev.robdoes.kmpresources.domain.repository

import com.intellij.openapi.vfs.VirtualFile

/**
 * Factory interface for creating instances of [ResourceRepository].
 *
 * This interface defines a method for constructing a resource repository based on a provided
 * virtual file. The resulting repository facilitates operations such as loading, parsing,
 * and managing XML-based resources within the application.
 */
internal interface ResourceRepositoryFactory {

    /**
     * Creates a new instance of [ResourceRepository] based on the given virtual file.
     *
     * This method initializes a resource repository that allows for managing, loading,
     * parsing, and modifying XML-based resources represented by the provided [VirtualFile].
     * The resulting repository enables various resource-related operations within the application.
     *
     * @param file The virtual file from which the resource repository will be created.
     *             This file serves as the source for managing XML resources.
     * @return A new instance of [ResourceRepository] for the given virtual file.
     */
    fun create(file: VirtualFile): ResourceRepository
}