package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveResourceUseCaseTest {
    @Test
    fun `saveResource should add a new resource to the repository`() = runBlocking {
        val fakeRepo = FakeResourceRepository()
        val saveUseCase = SaveResourceUseCase(fakeRepo)

        val newResource = StringResource("welcome", false, mapOf(null to "Hello KMP!"))
        saveUseCase(newResource)

        val resources = fakeRepo.loadResources()
        assertEquals(expected = 1, actual = resources.size)
        assertTrue(actual = resources.contains(newResource))
    }
}