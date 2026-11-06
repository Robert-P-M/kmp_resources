package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.application.service.ResourceSystemDetectionService
import dev.robdoes.kmpresources.core.shared.Bcp47FolderMapper

/**
 * Utility object for managing locale-specific XML files within a project structure.
 * Provides functionality to find and create localized XML files within specific
 * resource directories that adhere to Android's `values-` directory naming convention.
 */
internal object XmlLocaleFileManager {

    /**
     * Finds and retrieves a mapping of locale-specific directory tags to their corresponding XML files
     * for a given default resource file in the project.
     *
     * @param project The current IntelliJ project instance, used to access PsiManager and project-related resources.
     * @param defaultFile The default resource XML*/
    fun findRelatedLocaleFiles(project: Project, defaultFile: VirtualFile): Map<String, XmlFile> {
        val valuesDir = defaultFile.parent ?: return emptyMap()
        val composeResourcesDir = valuesDir.parent ?: return emptyMap()
        val psiManager = PsiManager.getInstance(project)
        val detectionService = project.service<ResourceSystemDetectionService>()
        val system = detectionService.detectSystem(defaultFile)

        return composeResourcesDir.children
            .filter { it.isDirectory && it.name.startsWith("${system.valuesDirPrefix}-") }
            .mapNotNull { dir ->
                val localeTag = Bcp47FolderMapper.folderNameToBcp47(dir.name, system.valuesDirPrefix)
                    ?: return@mapNotNull null

                val xmlFile = dir.findChild(defaultFile.name) ?: return@mapNotNull null
                val psiFile = psiManager.findFile(xmlFile) as? XmlFile ?: return@mapNotNull null

                localeTag to psiFile
            }.toMap()
    }

    /**
     * Creates or retrieves a locale-specific XML file based on a default XML file and locale tag.
     * The method ensures the file structure is created if it doesn't already exist, and initializes
     * the file with default content if it's empty.
     *
     * @param defaultFile The default XML file serving as a template for the localized version.
     * @param localeTag The locale tag (e.g., "fr", "es", "en") specifying the target localization.
     * @return The created or retrieved locale-specific XML file, or null if the operation fails.
     */
    suspend fun createLocaleFileInternal(
        defaultFile: VirtualFile,
        localeTag: String
    ): VirtualFile? {
        return edtWriteAction {
            val defaultDir = defaultFile.parent ?: return@edtWriteAction null
            val baseResourceDir = defaultDir.parent ?: return@edtWriteAction null

            val targetDirName = Bcp47FolderMapper.bcp47ToFolderName(localeTag)

            val targetDir = baseResourceDir.findChild(targetDirName)
                ?: baseResourceDir.createChildDirectory(this, targetDirName)

            val targetFile = targetDir.findChild(defaultFile.name)
                ?: targetDir.createChildData(this, defaultFile.name)

            if (targetFile.length == 0L) {
                val initialContent = "<resources>\n</resources>"
                targetFile.setBinaryContent(initialContent.toByteArray(Charsets.UTF_8))
            }
            targetFile.refresh(false, false)
            targetFile
        }
    }
}