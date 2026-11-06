package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.domain.model.AndroidNativeSystem
import dev.robdoes.kmpresources.domain.model.ComposeMultiplatformSystem
import dev.robdoes.kmpresources.domain.model.ResourceSystemProject

/**
 * A service for detecting the resource system used within a project.
 *
 * This service is designed to analyze virtual file paths and determine the type of resource
 * system applicable to the given file or project context. It provides support for identifying
 * resource systems such as Compose Multiplatform or Android Native based on specific path patterns.
 *
 * @constructor Initializes the service with a project instance.
 * @param project The project associated with this service.
 */
@Service(Service.Level.PROJECT)
internal class ResourceSystemDetectionService(private val project: Project) {

    /**
     * Detects the resource system type based on the provided file path.
     *
     * This method analyzes the given path to determine the type of resource system
     * used in the project. If the path is empty or does not match known patterns,
     * Compose Multiplatform System is returned as the default. If the path contains
     * specific Android resource-related segments, the method identifies it as an
     * Android Native System.
     *
     * @param path The file path to be analyzed for resource system detection.
     * @return The detected resource system of type `ResourceSystemProject`.
     * This will be either `ComposeMultiplatformSystem` or `AndroidNativeSystem`.
     */
    fun detectSystem(path: String): ResourceSystemProject {
        if (path.isEmpty()) return ComposeMultiplatformSystem

        val androidBase = AndroidNativeSystem.baseResourceDirName
        val androidValues = AndroidNativeSystem.valuesDirPrefix

        val isAndroidPath = path.contains("/src/main/$androidBase/") ||
                path.contains("/$androidBase/$androidValues")

        if (isAndroidPath) {
            return AndroidNativeSystem
        }

        return ComposeMultiplatformSystem
    }

    /**
     * Detects the resource system type based on the provided virtual file.
     *
     * This method determines the resource system by utilizing the file's path.
     * If the file is null or its path does not match known patterns, the method defaults
     * to returning the Compose Multiplatform System. If the path contains Android resource-specific
     * segments, it identifies the system as an Android Native System.
     *
     * @param file The virtual file whose path will be analyzed to detect the resource system.
     * Passing null will result in the default resource system being returned.
     * @return The detected resource system of type `ResourceSystemProject`.
     * This will be either `ComposeMultiplatformSystem` or `AndroidNativeSystem`.
     */
    fun detectSystem(file: VirtualFile?): ResourceSystemProject {
        return detectSystem(file?.path ?: "")
    }


}