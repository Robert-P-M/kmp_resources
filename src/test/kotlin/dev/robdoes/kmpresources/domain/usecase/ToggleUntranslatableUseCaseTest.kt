package dev.robdoes.kmpresources.domain.usecase

import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.FakeResourceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToggleUntranslatableUseCaseTest {

    @Test
    fun testToggleUntranslatableFlag() = runBlocking {
        val repo = FakeResourceRepository()
        repo.saveResource(StringResource("welcome", false, mapOf(null to "Welcome!")))

        val toggleUseCase = ToggleUntranslatableUseCase(repo)
        val loadUseCase = LoadResourcesUseCase(repo)

        toggleUseCase.invoke("welcome", true)
        assertTrue(actual = loadUseCase().first().isUntranslatable)

        toggleUseCase.invoke("welcome", false)
        assertFalse(actual = loadUseCase().first().isUntranslatable)
    }
}