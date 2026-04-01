package dev.robdoes.kmpresources.domain.usecase

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

internal class AddLocaleUseCaseTest : BasePlatformTestCase() {

    class FakeLocaleRepository : LocaleRepository {
        var contextsToReturn = listOf(AddLocaleUseCase.LocaleContext("path/values", "path/values/strings.xml"))
        var exists = false
        val createdLocaleTags = mutableListOf<String>()

        override suspend fun findAllDefaultLocaleContexts(): List<AddLocaleUseCase.LocaleContext> = contextsToReturn

        override suspend fun localeFileExists(context: AddLocaleUseCase.LocaleContext, localeTag: String): Boolean = exists

        override suspend fun createLocaleStructure(context: AddLocaleUseCase.LocaleContext, localeTag: String) {
            createdLocaleTags.add(localeTag)
        }
    }

    fun testInvokeAddsNewLocaleStructure() = runBlocking {
        val fakeLocaleRepo = FakeLocaleRepository()
        val useCase = AddLocaleUseCase(localeRepository = fakeLocaleRepo)

        useCase("fr")

        assertEquals(
            expected = 1,
            actual = fakeLocaleRepo.createdLocaleTags.size,
            message = "Should have triggered the creation of the locale structure exactly once"
        )
        assertEquals(
            expected = "fr",
            actual = fakeLocaleRepo.createdLocaleTags.first(),
            message = "The created locale tag should be 'fr'"
        )
    }

    fun testInvokeSkipsIfLocaleAlreadyExists() = runBlocking {
        val fakeLocaleRepo = FakeLocaleRepository()
        fakeLocaleRepo.exists = true

        val useCase = AddLocaleUseCase(localeRepository = fakeLocaleRepo)

        useCase("de")

        assertEquals(
            expected = 0,
            actual = fakeLocaleRepo.createdLocaleTags.size,
            message = "Should completely skip creation if the locale file already exists"
        )
    }
}