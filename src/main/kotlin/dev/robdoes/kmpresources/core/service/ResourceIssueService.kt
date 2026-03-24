package dev.robdoes.kmpresources.core.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryImpl
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.LoadResourcesUseCase

@Service(Service.Level.PROJECT)
class ResourceIssueService(private val project: Project) {

    private val logger = Logger.getInstance(ResourceIssueService::class.java)

    fun countIssues(file: VirtualFile): Int {
        if (!file.isValid) return 0
        val scanner = project.service<ResourceScannerService>()
        val repositoryFactory = project.service<XmlResourceRepositoryFactory>()

        return try {
            val keys = ReadAction.nonBlocking<List<String>> {
                val repository = repositoryFactory.create(file)
                val loadResourcesUseCase = LoadResourcesUseCase(repository)
                loadResourcesUseCase().map { it.key }
            }.executeSynchronously()

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

    fun findAllResourceFiles(): List<VirtualFile> {
        return try {
            ReadAction.nonBlocking<List<VirtualFile>> {
                val resourceFiles = mutableListOf<VirtualFile>()
                val projectScope = GlobalSearchScope.projectScope(project)

                FileTypeIndex.getFiles(XmlFileType.INSTANCE, projectScope).forEach { file ->
                    val isStringFile = file.name == "string.xml" || file.name == "strings.xml"

                    if (isStringFile && file.path.contains("composeResources")) {
                        resourceFiles.add(file)
                    }
                }
                resourceFiles
            }.executeSynchronously()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Error finding resource files", e)
            emptyList()
        }
    }
}