package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase

@Service(Service.Level.PROJECT)
class LocaleRepositoryFactory(private val project: Project) {

    fun createLocaleRepository(): XmlLocaleRepository = XmlLocaleRepository(project)

    fun resourceRepositoryFactory(): (AddLocaleUseCase.LocaleContext) -> ResourceRepository = { context ->
        val file = LocalFileSystem.getInstance().findFileByPath(context.defaultStringsFilePath)
            ?: error("File not found: ${context.defaultStringsFilePath}")
        XmlResourceRepositoryImpl(project, file)
    }
}
