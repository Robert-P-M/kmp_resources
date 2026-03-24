package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleUntranslatableUseCaseTest {

    @Test
    fun `invoke should correctly toggle the untranslatable flag of an existing resource`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("welcome_text", false, "Welcome!"))

        val toggleUseCase = ToggleUntranslatableUseCase(repo)
        val loadUseCase = LoadResourcesUseCase(repo)

        // Act - Set to true
        toggleUseCase(key = "welcome_text", isUntranslatable = true)

        // Assert
        val resourceAfterFirstToggle = loadUseCase().first()
        assertTrue(
            actual = resourceAfterFirstToggle.isUntranslatable,
            message = "The resource should now be marked as untranslatable (true)"
        )

        // Act - Set back to false
        toggleUseCase(key = "welcome_text", isUntranslatable = false)

        // Assert
        val resourceAfterSecondToggle = loadUseCase().first()
        assertFalse(
            actual = resourceAfterSecondToggle.isUntranslatable,
            message = "The resource should now be marked as translatable (false) again"
        )
    }
}