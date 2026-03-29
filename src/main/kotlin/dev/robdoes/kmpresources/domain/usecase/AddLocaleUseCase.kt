package dev.robdoes.kmpresources.domain.usecase

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

            localeRepository.createLocaleStructure(context, parsedLocale)
        }
    }
}
