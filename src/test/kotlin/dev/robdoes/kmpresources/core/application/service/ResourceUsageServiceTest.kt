package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceUsageServiceTest : BasePlatformTestCase() {

    fun testIsResourceUsedReturnsFalseForBlankKey() = runBlocking {
        // Arrange
        val scannerService = project.service<ResourceUsageService>()

        // Act & Assert
        assertFalse(
            actual = scannerService.isResourceUsed("   "),
            message = "Should return false immediately if the key is blank"
        )
        assertFalse(
            actual = scannerService.isResourceUsed(""),
            message = "Should return false immediately if the key is empty"
        )
    }

    fun testIsResourceUsedReturnsTrueWhenKeyExistsInKotlinFile() = runBlocking {
        // Arrange
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            """
            package dev.robdoes.ui
            
            import org.jetbrains.compose.resources.stringResource
            import kmpresources.generated.resources.*
            
            fun main() {
                val text = stringResource(Res.string.login_button_title)
            }
            """.trimIndent()
        )

        val scannerService = project.service<ResourceUsageService>()

        // Act & Assert
        assertTrue(
            actual = scannerService.isResourceUsed("login_button_title"),
            message = "The scanner should find 'login_button_title' in MainScreen.kt via the custom usage index."
        )
    }

    fun testIsResourceUsedReturnsFalseWhenKeyIsUnused() = runBlocking {
        // Arrange
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            "val text = stringResource(Res.string.some_other_key)"
        )

        val scannerService = project.service<ResourceUsageService>()

        // Act & Assert
        assertFalse(
            actual = scannerService.isResourceUsed("unused_key"),
            message = "The scanner should return false if the key does not exist in any indexed source file."
        )
    }

    fun testIsResourceUsedNormalizesKeyNames() = runBlocking {
        // Arrange
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            "val text = Res.string.my_weird_key"
        )

        val scannerService = project.service<ResourceUsageService>()

        // Act & Assert
        assertTrue(
            actual = scannerService.isResourceUsed("my.weird-key"),
            message = "The scanner should normalize dots and dashes to underscores before querying the index."
        )
    }
}