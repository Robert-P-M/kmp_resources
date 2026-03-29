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

@Service(Service.Level.PROJECT)
class ResourceIssueService(private val project: Project) {

    private val logger = Logger.getInstance(ResourceIssueService::class.java)

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
}