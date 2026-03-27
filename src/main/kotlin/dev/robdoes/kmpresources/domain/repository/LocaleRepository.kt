package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase.LocaleContext
import java.util.Locale

interface LocaleRepository {

    suspend fun findAllDefaultLocaleContexts(): List<LocaleContext>

    suspend fun localeFileExists(context: LocaleContext, locale: Locale): Boolean

    suspend fun createLocaleStructure(context: LocaleContext, locale: Locale)

    fun createLocaleRepository(
        context: LocaleContext,
        locale: Locale,
        factory: (LocaleContext) -> ResourceRepository
    ): ResourceRepository
}
