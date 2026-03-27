package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals

class LoadResourcesUseCaseTest {
    @Test
    fun `invoke should return all resources from the repository`() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("key_one", false, mapOf(null to "Value 1")))
        repo.saveResource(StringResource("key_two", true, mapOf(null to "Value 2")))

        val result = LoadResourcesUseCase(repo)()
        assertEquals(expected = 2, actual = result.size, message = "Should load exactly 2 resources")
    }
}