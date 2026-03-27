package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import java.util.*

class AddLocaleUseCase(
    private val localeRepository: LocaleRepository,
    private val resourceRepositoryFactory: (LocaleContext) -> ResourceRepository
) {

    data class LocaleContext(
        val defaultValuesDirPath: String,
        val defaultStringsFilePath: String
    )

    suspend operator fun invoke(localeTag: String) {
        val parsedLocale = Locale.forLanguageTag(localeTag)

        val contexts = localeRepository.findAllDefaultLocaleContexts()

        for (context in contexts) {
            if (localeRepository.localeFileExists(context, parsedLocale)) continue

            val defaultRepo = resourceRepositoryFactory(context)

            val defaultResources = com.intellij.openapi.application.readAction {
                defaultRepo.loadResources()
            }

            val filtered = defaultResources.filterNot { it.isUntranslatable }

            localeRepository.createLocaleStructure(context, parsedLocale)

            filtered.forEach { resource ->
                val emptyCopy = resource.copyWithEmptyLocale(localeTag)
                defaultRepo.saveResource(emptyCopy)
            }
        }
    }

    private fun XmlResource.copyWithEmptyLocale(newLocale: String): XmlResource = when (this) {
        is StringResource -> {
            copy(values = values + (newLocale to ""))
        }

        is StringArrayResource -> {
            val defaultSize = localizedItems[null]?.size ?: 0
            val emptyList = List(defaultSize) { "" }
            copy(localizedItems = localizedItems + (newLocale to emptyList))
        }

        is PluralsResource -> {
            val defaultKeys = localizedItems[null]?.keys ?: emptySet()
            val emptyPlurals = defaultKeys.associateWith { "" }
            copy(localizedItems = localizedItems + (newLocale to emptyPlurals))
        }
    }
}
