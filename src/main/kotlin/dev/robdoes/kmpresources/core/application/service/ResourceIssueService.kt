package dev.robdoes.kmpresources.core.application.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory

/**
 * Service for analyzing and managing resource usage and issues within a project.
 *
 * This service provides functionalities for counting unused resources, locating resource files,
 * and detecting missing translations in localized files.
 *
 * @property project The project context in which this service operates.
 */
@Service(Service.Level.PROJECT)
internal class ResourceIssueService(private val project: Project) {

    private val logger = Logger.getInstance(ResourceIssueService::class.java)

    /**
     * Counts the number of unused resource references in the provided file.
     *
     * This method analyzes the resources in the given file and checks their usage within the project.
     * If a resource is not used, it is counted as a warning.
     *
     * @param file the virtual file to be scanned for resource usage issues
     * @return the number of unused resource references in the file
     * @throws ProcessCanceledException if the operation is canceled
     */
    suspend fun countIssues(file: VirtualFile): Int {
        if (!file.isValid) return 0
        val scanner = project.service<ResourceUsageService>()
        val repositoryFactory = project.service<XmlResourceRepositoryFactory>()

        return try {
            val repository = repositoryFactory.create(file)
            val resources = repository.parseResourcesFromDisk()
            val keys = resources.map { it.key }

            var warnings = 0
            for (key in keys) {
                val isUsed = scanner.isResourceUsed(key)
                if (!isUsed) {
                    warnings++
                }
            }
            warnings
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error scanning file: ${file.path}", e)
            0
        }
    }

    /**
     * Finds all resource files in the project that match specific criteria.
     *
     * This method scans the project's XML files within the "composeResources" directory and identifies
     * files that meet the following conditions:
     * - The file extension is "xml".
     * - The file is located in a folder starting with "values".
     * - The root tag of the file is named "resources".
     *
     * The method performs the operation within a read action and handles any exceptions that
     * may occur, including `ProcessCanceledException` for cancellation scenarios.
     *
     * @return A list of `VirtualFile` objects representing the identified resource files.
     *         Returns an empty list if no matching files are found or in case of an error.
     */
    suspend fun findAllResourceFiles(): List<VirtualFile> {
        return try {
            readAction {
                val resourceFiles = mutableListOf<VirtualFile>()
                val projectScope = GlobalSearchScope.projectScope(project)
                val psiManager = PsiManager.getInstance(project)

                FileTypeIndex.getFiles(XmlFileType.INSTANCE, projectScope).forEach { file ->
                    if (file.extension == "xml" && file.parent?.name?.startsWith("values") == true && file.path.contains(
                            "composeResources"
                        )
                    ) {
                        val xmlFile = psiManager.findFile(file) as? XmlFile
                        if (xmlFile?.rootTag?.name == "resources") {
                            resourceFiles.add(file)
                        }
                    }
                }
                resourceFiles
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error finding resource files", e)
            emptyList()
        }
    }

    /**
     * Determines whether there are missing translations in the localized resource files compared to the default file.
     *
     * This method checks whether all the translatable keys in the default resource file are present in each of
     * the provided localized files. If any localized file is missing one or more keys found in the default file,
     * the method returns true.
     *
     * @param defaultFile the virtual file representing the default resource file
     * @param localizedFiles a list of virtual files representing the localized resource files to be checked
     * @return true if at least one localized file is missing translations for a key present in the default file, false otherwise
     */
    suspend fun hasMissingTranslations(defaultFile: VirtualFile, localizedFiles: List<VirtualFile>): Boolean {
        return readAction {
            val psiManager = PsiManager.getInstance(project)
            val defaultXml = psiManager.findFile(defaultFile) as? XmlFile ?: return@readAction false

            val defaultKeysToTranslate = defaultXml.rootTag?.subTags
                ?.filter { it.getAttributeValue("translatable") != "false" }
                ?.mapNotNull { it.getAttributeValue("name") }
                ?.toSet() ?: emptySet()

            if (defaultKeysToTranslate.isEmpty()) return@readAction false

            for (localeFile in localizedFiles) {
                val localeXml = psiManager.findFile(localeFile) as? XmlFile ?: continue
                val localeKeys = localeXml.rootTag?.subTags
                    ?.mapNotNull { it.getAttributeValue("name") }
                    ?.toSet() ?: emptySet()

                if (!localeKeys.containsAll(defaultKeysToTranslate)) {
                    return@readAction true
                }
            }

            false
        }
    }
}