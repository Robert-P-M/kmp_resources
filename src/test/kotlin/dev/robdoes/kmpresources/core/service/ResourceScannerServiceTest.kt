package dev.robdoes.kmpresources.core.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
            actual = scannerService.isResourceUsed("login_button_title"),
            message = "The scanner should find 'login_button_title' in MainScreen.kt"
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
            actual = scannerService.isResourceUsed("unused_key"),
            message = "The scanner should return false if the key does not exist in any file"
        )
    }

    fun testIsResourceUsedNormalizesKeyNames() {
        // Arrange
        // XML keys with dots/dashes are generated as underscores in Kotlin by KMP
        myFixture.addFileToProject(
            "src/commonMain/kotlin/MainScreen.kt",
            "val text = Res.string.my_weird_key"
        )

        val scannerService = project.service<ResourceScannerService>()

        // Act & Assert
        assertTrue(
            actual = scannerService.isResourceUsed("my.weird-key"),
            message = "The scanner should normalize dots and dashes to underscores before searching"
        )
    }
}