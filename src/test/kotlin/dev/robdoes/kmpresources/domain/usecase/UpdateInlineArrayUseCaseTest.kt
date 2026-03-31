package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

internal class UpdateInlineArrayUseCaseTest {

    @Test
    fun `invoke should add a new item when index is -1`() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(StringArrayResource("colors", false, mapOf(null to listOf("Red", "Green"))))

        val updateUseCase = UpdateInlineArrayUseCase(repo, LoadResourcesUseCase(repo))

        // Act - Index -1 means appending a new item
        updateUseCase(key = "colors", localeTag = null, isUntranslatable = false, index = -1, newValue = "Blue")

        // Assert
        val updatedArray = repo.loadResources().first() as StringArrayResource
        val items = updatedArray.localizedItems[null] ?: emptyList()
        assertEquals(expected = 3, actual = items.size)
        assertEquals(expected = "Blue", actual = items[2])
    }

    @Test
    fun `invoke should update existing item or remove it if blank`() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(StringArrayResource("animals", false, mapOf(null to listOf("Cat", "Dog", "Bird"))))

        val updateUseCase = UpdateInlineArrayUseCase(repo, LoadResourcesUseCase(repo))

        // Act
        updateUseCase(key = "animals", localeTag = null, isUntranslatable = false, index = 1, newValue = "Wolf")
        updateUseCase(key = "animals", localeTag = null, isUntranslatable = false, index = 2, newValue = "")

        // Assert
        val updatedArray = repo.loadResources().first() as StringArrayResource
        val items = updatedArray.localizedItems[null] ?: emptyList()
        assertEquals(expected = 2, actual = items.size)
        assertEquals(expected = "Wolf", actual = items[1])
    }
}