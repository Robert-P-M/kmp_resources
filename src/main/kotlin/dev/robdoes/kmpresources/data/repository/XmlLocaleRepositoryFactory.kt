package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import dev.robdoes.kmpresources.domain.repository.LocaleRepositoryFactory


@Service(Service.Level.PROJECT)
internal class XmlLocaleRepositoryFactory(private val project: Project) : LocaleRepositoryFactory {

    override fun createLocaleRepository(): XmlLocaleRepository = XmlLocaleRepository(project)

}
