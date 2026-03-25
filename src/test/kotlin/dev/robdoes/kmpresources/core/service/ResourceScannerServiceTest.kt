package dev.robdoes.kmpresources.core.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ResourceScannerServiceTest : BasePlatformTestCase() {

    fun testIsResourceUsedReturnsTrueWhenKeyExistsInKotlinFile() {
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

        val scannerService = project.service<ResourceScannerService>()

        // Act & Assert
        assertTrue(
            "The scanner should find 'login_button_title' in MainScreen.kt via the custom usage index.",
            scannerService.isResourceUsed("login_button_title")
        )
    }

    fun testIsResourceUsedReturnsFalseWhenKeyIsUnused() {
        // Arrange
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            "val text = stringResource(Res.string.some_other_key)"
        )

        val scannerService = project.service<ResourceScannerService>()

        // Act & Assert
        assertFalse(
            "The scanner should return false if the key does not exist in any indexed source file.",
            scannerService.isResourceUsed("unused_key")
        )
    }

    fun testIsResourceUsedNormalizesKeyNames() {
        // Arrange
        // KMP generates XML keys with dots/dashes as underscores in Kotlin
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            "val text = Res.string.my_weird_key"
        )

        val scannerService = project.service<ResourceScannerService>()

        // Act & Assert
        assertTrue(
            "The scanner should normalize dots and dashes to underscores before querying the index.",
            scannerService.isResourceUsed("my.weird-key")
        )
    }
}