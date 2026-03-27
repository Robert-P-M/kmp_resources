package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class UpdateInlineStringUseCaseTest {

    @Test
    fun `invoke should update the value of an existing string resource`() = runBlocking {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("greeting", false, mapOf(null to "Hello")))
        val updateUseCase = UpdateInlineStringUseCase(repo, LoadResourcesUseCase(repo))

        // Act
        updateUseCase(key = "greeting", localeTag = null, isUntranslatable = false, newValue = "Hi there")

        // Assert
        val updatedResource = repo.loadResources().first() as StringResource
        assertEquals(
            expected = "Hi there",
            actual = updatedResource.values[null],
            message = "The string value should be updated to the new value for the default locale"
        )
    }
}