package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals

class UpdateInlineStringUseCaseTest {

    @Test
    fun `invoke should update the value of an existing string resource`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("greeting", false, "Hello"))
        val updateUseCase = UpdateInlineStringUseCase(repo)

        // Act
        updateUseCase(key = "greeting", isUntranslatable = false, newValue = "Hi there")

        // Assert
        val updatedResource = repo.loadResources().first() as StringResource
        assertEquals(
            expected = "Hi there",
            actual = updatedResource.value,
            message = "The string value should be updated to the new value"
        )
    }
}