package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveResourceUseCaseTest {

    @Test
    fun `saveResource should add a new resource to the repository`() {
        // Arrange
        val fakeRepo = FakeResourceRepository()
        val saveUseCase = SaveResourceUseCase(fakeRepo)

        val newResource = StringResource(
            key = "welcome_message",
            isUntranslatable = false,
            value = "Hello KMP!"
        )

        // Act
        saveUseCase(newResource)

        // Assert
        val resources = fakeRepo.loadResources()
        assertEquals(
            expected = 1,
            actual = resources.size,
            message = "The repository should contain exactly 1 resource"
        )
        assertTrue(
            actual = resources.contains(newResource),
            message = "The saved resource should be found in the repository"
        )
    }
}