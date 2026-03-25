package dev.robdoes.kmpresources.ide.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmpDocumentationProviderTest : BasePlatformTestCase() {

    fun testGenerateDocForStringResource() {
        // Arrange: Create XML and Kotlin file
        val xmlContent = """
            <resources>
                <string name="login_title">Welcome to the App!</string>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Set the caret inside the key
        myFixture.configureByText(
            "MainScreen.kt",
            "val text = Res.string.log<caret>in_title"
        )
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)

        val provider = KmpDocumentationProvider()

        // Act 1: Get the custom target element (our KmpResourceTarget)
        val docElement = provider.getCustomDocumentationElement(
            myFixture.editor,
            myFixture.file,
            elementAtCaret,
            myFixture.caretOffset
        )
        assertNotNull(
            actual = docElement,
            message = "The documentation provider should resolve the element under the caret"
        )

        // Act 2: Generate the HTML documentation
        val htmlDoc = provider.generateDoc(docElement, elementAtCaret)

        // Assert
        assertNotNull(
            actual = htmlDoc,
            message = "The generated HTML documentation should not be null"
        )
        assertTrue(
            actual = htmlDoc.contains("login_title"),
            message = "The documentation should display the key name"
        )
        assertTrue(
            actual = htmlDoc.contains("Welcome to the App!"),
            message = "The documentation should display the actual string value from the XML"
        )
        assertTrue(
            actual = htmlDoc.contains("string"),
            message = "The documentation should mention the resource type"
        )
    }

    fun testGenerateDocForPluralResource() {
        // Arrange
        val xmlContent = """
            <resources>
                <plurals name="apples">
                    <item quantity="one">1 apple</item>
                    <item quantity="other">Many apples</item>
                </plurals>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        myFixture.configureByText("MainScreen.kt", "val p = Res.plurals.app<caret>les")
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
        val provider = KmpDocumentationProvider()

        // Act
        val docElement = provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, elementAtCaret, myFixture.caretOffset
        )
        val htmlDoc = provider.generateDoc(docElement, elementAtCaret)

        // Assert
        assertNotNull(actual = htmlDoc)
        assertTrue(
            actual = htmlDoc.contains("1 apple") && htmlDoc.contains("Many apples"),
            message = "The documentation should list all plural items and their values"
        )
    }
}