package dev.robdoes.kmpresources.ide.navigation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class KmpGotoDeclarationHandlerTest : BasePlatformTestCase() {

    fun testGotoDeclarationResolvesToXmlTag() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="target_key">Target Value</string>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Set the caret inside the key in the Kotlin file
        myFixture.configureByText(
            "MainScreen.kt",
            "val text = Res.string.tar<caret>get_key"
        )

        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
        val handler = KmpGotoDeclarationHandler()

        // Act
        val targets = handler.getGotoDeclarationTargets(
            elementAtCaret,
            myFixture.caretOffset,
            myFixture.editor
        )

        // Assert
        assertNotNull(
            actual = targets,
            message = "Targets array should not be null"
        )
        assertTrue(
            actual = targets.isNotEmpty(),
            message = "Should find at least one navigation target"
        )

        val target = targets[0]
        assertTrue(
            actual = target is KmpResourceTarget,
            message = "The target should be an instance of KmpResourceTarget"
        )

        val kmpTarget = target
        assertEquals(
            expected = "target_key",
            actual = kmpTarget.name,
            message = "The target should point to the correct XML key name"
        )
    }
}