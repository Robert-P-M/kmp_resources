package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.repository.ResourceRepositoryFactory


@Service(Service.Level.PROJECT)
internal class XmlResourceRepositoryFactory(private val project: Project) : ResourceRepositoryFactory {
    override fun create(file: VirtualFile): ResourceRepository {
        return XmlResourceRepositoryImpl(project, file)
    }
}