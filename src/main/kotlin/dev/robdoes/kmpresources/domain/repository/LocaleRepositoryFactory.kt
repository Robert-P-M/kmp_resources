package dev.robdoes.kmpresources.domain.repository

import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase

interface LocaleRepositoryFactory {
    fun createLocaleRepository(): LocaleRepository
    fun resourceRepositoryFactory(): (AddLocaleUseCase.LocaleContext) -> ResourceRepository
}