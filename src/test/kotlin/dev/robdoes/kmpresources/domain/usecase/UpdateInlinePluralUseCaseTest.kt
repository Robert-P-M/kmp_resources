package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateInlinePluralUseCaseTest {

    @Test
    fun `invoke should update an existing quantity or add a new one`() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(PluralsResource("apples", false, mapOf(null to mapOf("one" to "1 apple"))))

        val loadUseCase = LoadResourcesUseCase(repo)
        val updateUseCase = UpdateInlinePluralUseCase(repo, loadUseCase)

        // Act - Update existing & Add new
        updateUseCase(key = "apples", localeTag = null, isUntranslatable = false, quantity = "one", newValue = "One apple")
        updateUseCase(key = "apples", localeTag = null, isUntranslatable = false, quantity = "other", newValue = "Many apples")

        // Assert
        val updatedPlural = repo.loadResources().first() as PluralsResource
        val items = updatedPlural.localizedItems[null] ?: emptyMap()
        assertEquals(expected = 2, actual = items.size)
        assertEquals(expected = "One apple", actual = items["one"])
        assertEquals(expected = "Many apples", actual = items["other"])
    }

    @Test
    fun `invoke with blank value should remove the quantity`() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(PluralsResource("dogs", false, mapOf(null to mapOf("one" to "1 dog", "other" to "Many dogs"))))

        val updateUseCase = UpdateInlinePluralUseCase(repo, LoadResourcesUseCase(repo))

        // Act - Pass a blank string to trigger deletion
        updateUseCase(key = "dogs", localeTag = null, isUntranslatable = false, quantity = "one", newValue = "   ")

        // Assert
        val updatedPlural = repo.loadResources().first() as PluralsResource
        val items = updatedPlural.localizedItems[null] ?: emptyMap()
        assertFalse(actual = items.containsKey("one"))
        assertTrue(actual = items.containsKey("other"))
    }
}