package dev.robdoes.kmpresources.presentation.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

/**
 * Represents a custom virtual file used in the KMP Resource editor to manage and display
 * localized resource data for Kotlin Multiplatform projects.
 *
 * This virtual file is tailored specifically for handling structured resource information
 * and is used as a read-only representation of the resources within the editor workflow.
 * It extends the `LightVirtualFile` to provide lightweight virtual file functionality,
 * and integrates with the IntelliJ platform to represent the KMP resource data effectively.
 *
 * The main features of this class include:
 * - A custom name format derived from the module path for improved identification.
 * - Association with a default strings file as the underlying resource file.
 * - Marked as non-writable for read-only usage in the context of resource management.
 * - Custom equality and hash code logic based on the path of the associated default strings file.
 *
 * @param modulePath The path of the module to which the resource file belongs, used for naming purposes.
 * @param defaultStringsFile The underlying `VirtualFile` that represents the default resource strings.
 */
internal class KmpResourceVirtualFile(
    modulePath: String,
    val defaultStringsFile: VirtualFile
) : LightVirtualFile("KMP Resources - $modulePath") {

    init {
        isWritable = false
    }

    override fun getFileType(): FileType {
        return KmpResourceFileType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KmpResourceVirtualFile) return false
        return defaultStringsFile.path == other.defaultStringsFile.path
    }

    override fun hashCode(): Int {
        return defaultStringsFile.path.hashCode()
    }

    companion object {
        /**
         * Safely and quickly computes the readable Gradle module path (e.g. "feature:timer:setup")
         * without hitting the slow WorkspaceFileIndex. This is completely safe to call on the EDT.
         */
        fun computeModuleName(project: Project, file: VirtualFile): String {
            val basePath = project.basePath ?: return file.parent.name

            // Example: /Users/rob/projects/App/feature/timer/src/... -> /feature/timer/
            val relativePath = file.path
                .substringAfter(basePath)
                .substringBefore("src")
                .replace("\\\\", "/")
                .removePrefix("/")
                .removeSuffix("/")

            // Convert path slashes to Gradle colons (feature/timer -> feature:timer)
            return relativePath.replace("/", ":").ifEmpty { "app" }
        }
    }
}