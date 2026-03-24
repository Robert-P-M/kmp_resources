package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateInlinePluralUseCaseTest {

    @Test
    fun `invoke should update an existing quantity or add a new one`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(PluralsResource("apples", false, mapOf("one" to "1 apple")))

        val loadUseCase = LoadResourcesUseCase(repo)
        val updateUseCase = UpdateInlinePluralUseCase(repo, loadUseCase)

        // Act - Update existing
        updateUseCase(key = "apples", isUntranslatable = false, quantity = "one", newValue = "One single apple")
        // Act - Add new
        updateUseCase(key = "apples", isUntranslatable = false, quantity = "other", newValue = "Many apples")

        // Assert
        val updatedPlural = repo.loadResources().first() as PluralsResource
        assertEquals(
            expected = 2,
            actual = updatedPlural.items.size,
            message = "There should be 2 plural items now"
        )
        assertEquals(
            expected = "One single apple",
            actual = updatedPlural.items["one"],
            message = "The 'one' quantity should be updated"
        )
        assertEquals(
            expected = "Many apples",
            actual = updatedPlural.items["other"],
            message = "The 'other' quantity should be added"
        )
    }

    @Test
    fun `invoke with blank value should remove the quantity`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(PluralsResource("dogs", false, mapOf("one" to "1 dog", "other" to "Many dogs")))

        val loadUseCase = LoadResourcesUseCase(repo)
        val updateUseCase = UpdateInlinePluralUseCase(repo, loadUseCase)

        // Act - Pass a blank string to trigger deletion
        updateUseCase(key = "dogs", isUntranslatable = false, quantity = "one", newValue = "   ")

        // Assert
        val updatedPlural = repo.loadResources().first() as PluralsResource
        assertFalse(
            actual = updatedPlural.items.containsKey("one"),
            message = "The quantity 'one' should be removed because the new value was blank"
        )
        assertTrue(
            actual = updatedPlural.items.containsKey("other"),
            message = "The quantity 'other' should remain intact"
        )
    }
}