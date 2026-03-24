package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals

class UpdateInlineArrayUseCaseTest {

    @Test
    fun `invoke should add a new item when index is -1`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringArrayResource("colors", false, listOf("Red", "Green")))

        val loadUseCase = LoadResourcesUseCase(repo)
        val updateUseCase = UpdateInlineArrayUseCase(repo, loadUseCase)

        // Act - Index -1 means appending a new item
        updateUseCase(key = "colors", isUntranslatable = false, index = -1, newValue = "Blue")

        // Assert
        val updatedArray = repo.loadResources().first() as StringArrayResource
        assertEquals(
            expected = 3,
            actual = updatedArray.items.size,
            message = "The array should now contain 3 items"
        )
        assertEquals(
            expected = "Blue",
            actual = updatedArray.items[2],
            message = "The new item should be appended at the end"
        )
    }

    @Test
    fun `invoke should update existing item or remove it if blank`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringArrayResource("animals", false, listOf("Cat", "Dog", "Bird")))

        val loadUseCase = LoadResourcesUseCase(repo)
        val updateUseCase = UpdateInlineArrayUseCase(repo, loadUseCase)

        // Act - Update index 1
        updateUseCase(key = "animals", isUntranslatable = false, index = 1, newValue = "Wolf")
        // Act - Remove index 2 by passing a blank string
        updateUseCase(key = "animals", isUntranslatable = false, index = 2, newValue = "")

        // Assert
        val updatedArray = repo.loadResources().first() as StringArrayResource
        assertEquals(
            expected = 2,
            actual = updatedArray.items.size,
            message = "The array should contain 2 items after one was removed"
        )
        assertEquals(
            expected = "Wolf",
            actual = updatedArray.items[1],
            message = "The item at index 1 should be updated to Wolf"
        )
    }
}