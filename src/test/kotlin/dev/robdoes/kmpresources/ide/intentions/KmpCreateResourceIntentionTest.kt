package dev.robdoes.kmpresources.ide.intentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class KmpCreateResourceIntentionTest : BasePlatformTestCase() {

    fun testIntentionIsAvailableAndGeneratesStringResource() {
        // Arrange
        val stringsFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>")

        myFixture.configureByText(
            "MainScreen.kt",
            """
            package dev.robdoes.ui
            import kmpresources.generated.resources.Res
            
            fun test() {
                val t = Res.string.missin<caret>g_key
            }
            """.trimIndent()
        )

        // Act 1: Check availability
        val intention = KmpCreateResourceIntention()
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!

        val isAvailable = intention.isAvailable(project, myFixture.editor, elementAtCaret)

        // Assert 1
        assertTrue(
            actual = isAvailable,
            message = "Intention should be available on an unresolved Res.string reference"
        )

        // Act 2: Invoke intention
        intention(project, myFixture.editor, elementAtCaret)

        // Assert 2: Verify the strings.xml was updated
        val xmlText = stringsFile.text
        assertTrue(
            actual = xmlText.contains("""<string name="missing_key">Test Value</string>"""),
            message = "The XML file should now contain the generated string resource"
        )

        // Assert 3: Verify the import was added to the Kotlin file
        val ktText = myFixture.file.text
        assertTrue(
            actual = ktText.contains("import kmpresources.generated.resources.missing_key"),
            message = "The specific import for the new key should be added to the Kotlin file"
        )
    }

    fun testIntentionGeneratesPluralsResourceCorrectly() {
        // Arrange
        val stringsFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>")
        myFixture.configureByText(
            "MainScreen.kt",
            """
            package dev.robdoes.ui
            import kmpresources.generated.resources.Res
            
            fun test() { val p = Res.plurals.new_pl<caret>ural }
            """.trimIndent()
        )

        val intention = KmpCreateResourceIntention()
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act
        intention(project, myFixture.editor, elementAtCaret)

        // Assert
        val xmlText = stringsFile.text
        assertTrue(
            actual = xmlText.contains("""<plurals name="new_plural">"""),
            message = "Should generate a <plurals> tag"
        )
        assertTrue(
            actual = xmlText.contains("""<item quantity="other">Test Value</item>"""),
            message = "Should generate an 'other' quantity item by default"
        )
    }

    fun testIntentionIsNotAvailableOnRandomCode() {
        // Arrange
        myFixture.configureByText(
            "MainScreen.kt",
            "val someVaria<caret>ble = 42"
        )

        val intention = KmpCreateResourceIntention()
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act
        val isAvailable = intention.isAvailable(project, myFixture.editor, elementAtCaret)

        // Assert
        assertFalse(
            actual = isAvailable,
            message = "Intention must not be available on arbitrary code elements"
        )
    }
}