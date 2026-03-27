package dev.robdoes.kmpresources.ide.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmpDocumentationProviderTest : BasePlatformTestCase() {

    fun testGenerateDocForStringResource() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="login_title">Welcome to the App!</string>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        myFixture.configureByText(
            "MainScreen.kt",
            "val text = Res.string.log<caret>in_title"
        )
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)
        val provider = KmpDocumentationProvider()

        // Act
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
        assertTrue(
            actual = htmlDoc.contains("<ul>") && htmlDoc.contains("<li>"),
            message = "Plurals should be formatted as an HTML unordered list"
        )
    }

    fun testGenerateDocForArrayResource() {
        // Arrange
        val xmlContent = """
            <resources>
                <string-array name="options">
                    <item>First Choice</item>
                    <item>Second Choice</item>
                </string-array>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        myFixture.configureByText("MainScreen.kt", "val a = Res.array.opt<caret>ions")
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
            actual = htmlDoc.contains("First Choice") && htmlDoc.contains("Second Choice"),
            message = "The documentation should list all array items"
        )
        assertTrue(
            actual = htmlDoc.contains("<ol>") && htmlDoc.contains("<li>"),
            message = "Arrays should be formatted as an HTML ordered list"
        )
    }

    fun testGenerateDocIncludesTranslationLinks() {
        // Arrange
        val defaultXml = """<resources><string name="greeting">Hello</string></resources>"""
        val germanXml = """<resources><string name="greeting">Hallo</string></resources>"""
        val frenchXml = """<resources><string name="greeting">Bonjour</string></resources>"""

        myFixture.addFileToProject("composeResources/values/strings.xml", defaultXml)
        myFixture.addFileToProject("composeResources/values-de/strings.xml", germanXml)
        myFixture.addFileToProject("composeResources/values-fr/strings.xml", frenchXml)

        myFixture.configureByText("MainScreen.kt", "val t = Res.string.gre<caret>eting")
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
            actual = htmlDoc.contains("psi_element://locale_"),
            message = "The documentation should contain specialized psi_element links for translations"
        )
        assertTrue(
            actual = htmlDoc.contains(">[de]</a>"),
            message = "The documentation should contain a link to the German translation"
        )
        assertTrue(
            actual = htmlDoc.contains(">[fr]</a>"),
            message = "The documentation should contain a link to the French translation"
        )
    }
}