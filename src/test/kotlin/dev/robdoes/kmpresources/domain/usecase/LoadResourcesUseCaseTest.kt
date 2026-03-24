package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals

class LoadResourcesUseCaseTest {

    @Test
    fun `invoke should return all resources from the repository`() {
        // Arrange
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("key_one", false, "Value 1"))
        repo.saveResource(StringResource("key_two", true, "Value 2"))

        val loadUseCase = LoadResourcesUseCase(repo)

        // Act
        val result = loadUseCase()

        // Assert
        assertEquals(
            expected = 2,
            actual = result.size,
            message = "The use case should load exactly 2 resources from the repository"
        )
    }
}