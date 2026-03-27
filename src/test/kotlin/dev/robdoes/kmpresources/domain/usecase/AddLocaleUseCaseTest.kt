package dev.robdoes.kmpresources.domain.usecase

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddLocaleUseCaseTest : BasePlatformTestCase() {

    class FakeLocaleRepository : LocaleRepository {
        var contextsToReturn = listOf(AddLocaleUseCase.LocaleContext("path/values", "path/values/strings.xml"))
        var exists = false
        val createdLocales = mutableListOf<Locale>()

        override suspend fun findAllDefaultLocaleContexts(): List<AddLocaleUseCase.LocaleContext> = contextsToReturn

        override suspend fun localeFileExists(context: AddLocaleUseCase.LocaleContext, locale: Locale): Boolean = exists

        override suspend fun createLocaleStructure(context: AddLocaleUseCase.LocaleContext, locale: Locale) {
            createdLocales.add(locale)
        }

        override fun createLocaleRepository(
            context: AddLocaleUseCase.LocaleContext,
            locale: Locale,
            factory: (AddLocaleUseCase.LocaleContext) -> ResourceRepository
        ): ResourceRepository {
            return factory(context)
        }
    }

    fun testInvokeAddsNewLocaleToTranslatableResources() = runBlocking {
        // Arrange
        val fakeLocaleRepo = FakeLocaleRepository()
        val fakeResourceRepo = FakeResourceRepository()

        // Populate the fake repository with all types of resources
        fakeResourceRepo.saveResource(StringResource("greeting", false, mapOf(null to "Hello")))
        fakeResourceRepo.saveResource(StringResource("app_name", true, mapOf(null to "My App"))) // Untranslatable!
        fakeResourceRepo.saveResource(PluralsResource("apples", false, mapOf(null to mapOf("one" to "1 apple", "other" to "Many"))))
        fakeResourceRepo.saveResource(StringArrayResource("options", false, mapOf(null to listOf("A", "B"))))

        val useCase = AddLocaleUseCase(
            localeRepository = fakeLocaleRepo,
            resourceRepositoryFactory = { fakeResourceRepo }
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

        val resources = fakeResourceRepo.loadResources()

        // 1. Verify Translatable String
        val greeting = resources.find { it.key == "greeting" } as StringResource
        assertTrue(
            actual = greeting.values.containsKey("fr"),
            message = "Translatable strings should get the new 'fr' key"
        )
        assertEquals(
            expected = "",
            actual = greeting.values["fr"],
            message = "The new translation template should be an empty string"
        )

        // 2. Verify Untranslatable String
        val appName = resources.find { it.key == "app_name" } as StringResource
        assertFalse(
            actual = appName.values.containsKey("fr"),
            message = "Untranslatable strings MUST NOT receive a new locale key"
        )

        // 3. Verify Plurals
        val apples = resources.find { it.key == "apples" } as PluralsResource
        assertTrue(
            actual = apples.localizedItems.containsKey("fr"),
            message = "Translatable plurals should get the new 'fr' key"
        )
        val frApples = apples.localizedItems["fr"]!!
        assertEquals(
            expected = "",
            actual = frApples["one"],
            message = "Plural sub-item 'one' should be an empty string"
        )
        assertEquals(
            expected = "",
            actual = frApples["other"],
            message = "Plural sub-item 'other' should be an empty string"
        )

        // 4. Verify Array
        val options = resources.find { it.key == "options" } as StringArrayResource
        assertTrue(
            actual = options.localizedItems.containsKey("fr"),
            message = "Translatable arrays should get the new 'fr' key"
        )
        val frOptions = options.localizedItems["fr"]!!
        assertEquals(
            expected = 2,
            actual = frOptions.size,
            message = "The generated array should have the exact same size as the default array"
        )
        assertEquals(expected = "", actual = frOptions[0], message = "First array item should be empty")
        assertEquals(expected = "", actual = frOptions[1], message = "Second array item should be empty")
    }

    fun testInvokeSkipsIfLocaleAlreadyExists() = runBlocking {
        // Arrange
        val fakeLocaleRepo = FakeLocaleRepository()
        fakeLocaleRepo.exists = true // Simulate that the locale directory already exists!

        val fakeResourceRepo = FakeResourceRepository()
        fakeResourceRepo.saveResource(StringResource("greeting", false, mapOf(null to "Hello")))

        val useCase = AddLocaleUseCase(
            localeRepository = fakeLocaleRepo,
            resourceRepositoryFactory = { fakeResourceRepo }
        )

        // Act
        useCase("de")

        // Assert
        assertEquals(
            expected = 0,
            actual = fakeLocaleRepo.createdLocales.size,
            message = "Should completely skip creation if the locale file already exists"
        )

        val greeting = fakeResourceRepo.loadResources().first() as StringResource
        assertFalse(
            actual = greeting.values.containsKey("de"),
            message = "Should not generate or save any empty templates if skipped"
        )
    }
}