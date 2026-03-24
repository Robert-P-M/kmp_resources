package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeleteResourceUseCaseTest {

    @Test
    fun `deleting a main resource should remove it completely from repository`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("app_name", false, "My App"))
        repo.saveResource(StringResource("login_text", false, "Login"))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act
        deleteUseCase(key = "app_name", type = "string", isSubItem = false)

        // Assert
        val remaining = repo.loadResources()
        assertEquals(
            expected = 1,
            actual = remaining.size,
            message = "There should be exactly 1 resource left"
        )
        assertFalse(
            actual = remaining.any { it.key == "app_name" },
            message = "The deleted resource 'app_name' should not exist anymore"
        )
    }

    @Test
    fun `deleting a plural sub-item should only remove the specific quantity`() {
        // Arrange
        val repo = FakeResourceRepository()
        val initialItems = mapOf("one" to "1 item", "other" to "Many items")
        repo.saveResource(PluralsResource("item_count", false, initialItems))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act
        deleteUseCase(key = "item_count", type = "one", isSubItem = true)

        // Assert
        val pluralRes = repo.loadResources().first() as PluralsResource
        assertEquals(
            expected = 1,
            actual = pluralRes.items.size,
            message = "Only one plural item should remain"
        )
        assertFalse(
            actual = pluralRes.items.containsKey("one"),
            message = "The quantity 'one' should be deleted"
        )
        assertTrue(
            actual = pluralRes.items.containsKey("other"),
            message = "The quantity 'other' should still exist"
        )
    }

    @Test
    fun `deleting an array sub-item should remove it by index`() {
        // Arrange
        val repo = FakeResourceRepository()
        val initialList = listOf("First", "Second", "Third")
        repo.saveResource(StringArrayResource("options_array", false, initialList))

        val loadUseCase = LoadResourcesUseCase(repo)
        val deleteUseCase = DeleteResourceUseCase(repo, loadUseCase)

        // Act - Delete the middle item "Second" at index 1
        deleteUseCase(key = "options_array", type = "item[1]", isSubItem = true)

        // Assert
        val arrayRes = repo.loadResources().first() as StringArrayResource
        assertEquals(
            expected = 2,
            actual = arrayRes.items.size,
            message = "The array should now contain 2 items"
        )
        assertEquals(
            expected = "First",
            actual = arrayRes.items[0],
            message = "First item should remain unchanged"
        )
        assertEquals(
            expected = "Third",
            actual = arrayRes.items[1],
            message = "Third item should have shifted to index 1"
        )
    }
}