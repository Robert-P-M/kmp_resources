package dev.robdoes.kmpresources.domain.usecase

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.assertEquals

internal class AddLocaleUseCaseTest : BasePlatformTestCase() {

    class FakeLocaleRepository : LocaleRepository {
        var contextsToReturn = listOf(AddLocaleUseCase.LocaleContext("path/values", "path/values/strings.xml"))
        var exists = false
        val createdLocales = mutableListOf<Locale>()

        override suspend fun findAllDefaultLocaleContexts(): List<AddLocaleUseCase.LocaleContext> = contextsToReturn

        override suspend fun localeFileExists(context: AddLocaleUseCase.LocaleContext, locale: Locale): Boolean = exists

        override suspend fun createLocaleStructure(context: AddLocaleUseCase.LocaleContext, locale: Locale) {
            createdLocales.add(locale)
        }

    }

    fun testInvokeAddsNewLocaleStructure() = runBlocking {
        // Arrange
        val fakeLocaleRepo = FakeLocaleRepository()

        val useCase = AddLocaleUseCase(
            localeRepository = fakeLocaleRepo,
        )

        // Act
        useCase("fr")

        // Assert: Verify Locale Structure was created
        assertEquals(
            expected = 1,
            actual = fakeLocaleRepo.createdLocales.size,
            message = "Should have triggered the creation of the locale structure exactly once"
        )
        assertEquals(
            expected = "fr",
            actual = fakeLocaleRepo.createdLocales.first().language,
            message = "The created locale should be French"
        )
    }

    fun testInvokeSkipsIfLocaleAlreadyExists() = runBlocking {
        // Arrange
        val fakeLocaleRepo = FakeLocaleRepository()
        fakeLocaleRepo.exists = true // Simulate that the locale directory already exists!


        val useCase = AddLocaleUseCase(
            localeRepository = fakeLocaleRepo,
        )

        // Act
        useCase("de")

        // Assert
        assertEquals(
            expected = 0,
            actual = fakeLocaleRepo.createdLocales.size,
            message = "Should completely skip creation if the locale file already exists"
        )
    }
}