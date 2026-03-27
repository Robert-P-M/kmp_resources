package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteResourceUseCaseTest {

    @Test
    fun `deleting a main resource should remove it completely from repository`() = runBlocking {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("app_name", false, mapOf(null to "My App")))
        repo.saveResource(StringResource("login_text", false, mapOf(null to "Login")))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act
        deleteUseCase(key = "app_name", resourceType = ResourceType.String, isSubItem = false)

        // Assert
        val remaining = repo.loadResources()
        assertEquals(expected = 1, actual = remaining.size, message = "There should be exactly 1 resource left")
        assertFalse(actual = remaining.any { it.key == "app_name" }, message = "The deleted resource 'app_name' should not exist anymore")
    }

    @Test
    fun `deleting a plural sub-item should only remove the specific quantity`() = runBlocking {
        // Arrange
        val repo = FakeResourceRepository()
        val initialItems = mapOf("one" to "1 item", "other" to "Many items")
        repo.saveResource(PluralsResource("item_count", false, mapOf(null to initialItems)))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act
        deleteUseCase(key = "item_count", resourceType = ResourceType.Plural, isSubItem = true, subItemIdentifier = "one")

        // Assert
        val pluralRes = repo.loadResources().first() as PluralsResource
        val defaultItems = pluralRes.localizedItems[null] ?: emptyMap()
        assertEquals(expected = 1, actual = defaultItems.size, message = "Only one plural item should remain")
        assertFalse(actual = defaultItems.containsKey("one"), message = "The quantity 'one' should be deleted")
        assertTrue(actual = defaultItems.containsKey("other"), message = "The quantity 'other' should still exist")
    }

    @Test
    fun `deleting an array sub-item should remove it by index`() = runBlocking {
        // Arrange
        val repo = FakeResourceRepository()
        val initialList = listOf("First", "Second", "Third")
        repo.saveResource(StringArrayResource("options_array", false, mapOf(null to initialList)))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act - Delete the middle item "Second" at index 1
        // FIX: Pass "item[1]" exactly as the UI table would do!
        deleteUseCase(
            key = "options_array",
            resourceType = ResourceType.Array,
            isSubItem = true,
            subItemIdentifier = "item[1]"
        )

        // Assert
        val arrayRes = repo.loadResources().first() as StringArrayResource
        val defaultItems = arrayRes.localizedItems[null] ?: emptyList()
        assertEquals(expected = 2, actual = defaultItems.size, message = "The array should now contain 2 items")
        assertEquals(expected = "First", actual = defaultItems[0], message = "First item should remain unchanged")
        assertEquals(expected = "Third", actual = defaultItems[1], message = "Third item should have shifted to index 1")
    }
}