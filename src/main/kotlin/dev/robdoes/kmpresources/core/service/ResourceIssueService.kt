package dev.robdoes.kmpresources.core.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryImpl
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.LoadResourcesUseCase

@Service(Service.Level.PROJECT)
class ResourceIssueService(private val project: Project) {

    private val logger = Logger.getInstance(ResourceIssueService::class.java)

    fun countIssues(file: VirtualFile): Int {
        if (!file.isValid) return 0
        val scanner = project.service<ResourceScannerService>()

        return try {
            val keys = ReadAction.compute<List<String>, Throwable> {
                val repository: ResourceRepository = XmlResourceRepositoryImpl(project, file)
                val loadResourcesUseCase = LoadResourcesUseCase(repository)
                loadResourcesUseCase().map { it.key }
            }

            var warnings = 0
            for (key in keys) {
                if (!scanner.isResourceUsed(key)) {
                    warnings++
                }
            }
            warnings
        } catch (e: Exception) {
            logger.warn("Error scanning file: ${file.path}", e)
            0
        }
    }

    fun findAllResourceFiles(): List<VirtualFile> {
        return try {
            ReadAction.compute<List<VirtualFile>, Throwable> {
                val resourceFiles = mutableListOf<VirtualFile>()
                val projectScope = GlobalSearchScope.projectScope(project)

                FileTypeIndex.getFiles(XmlFileType.INSTANCE, projectScope).forEach { file ->
                    val isStringFile = file.name == "string.xml" || file.name == "strings.xml"

                    if (isStringFile && file.path.contains("composeResources")) {
                        resourceFiles.add(file)
                    }
                }
                resourceFiles
            }
        } catch (e: Exception) {
            logger.warn("Error finding resource files", e)
            emptyList()
        }
    }
}