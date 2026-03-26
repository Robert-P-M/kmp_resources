package dev.robdoes.kmpresources.data.repository

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XmlResourceRepositoryImplTest : BasePlatformTestCase() {

    fun testLoadAllResourceTypesCorrectly() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="app_name">My App</string>
                <plurals name="item_count" translatable="false">
                    <item quantity="one">1 item</item>
                    <item quantity="other">Many items</item>
                </plurals>
                <string-array name="options">
                    <item>First</item>
                    <item>Second</item>
                </string-array>
            </resources>
        """.trimIndent()

        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act
        val resources = repository.loadResources()

        // Assert
        assertEquals(expected = 3, actual = resources.size, message = "Should load exactly 3 resources")

        val stringRes = resources.find { it.key == "app_name" } as StringResource
        assertEquals(expected = "My App", actual = stringRes.value)
        assertFalse(actual = stringRes.isUntranslatable)

        val pluralRes = resources.find { it.key == "item_count" } as PluralsResource
        assertEquals(expected = 2, actual = pluralRes.items.size)
        assertEquals(expected = "1 item", actual = pluralRes.items["one"])
        assertTrue(actual = pluralRes.isUntranslatable)

        val arrayRes = resources.find { it.key == "options" } as StringArrayResource
        assertEquals(expected = 2, actual = arrayRes.items.size)
        assertEquals(expected = "First", actual = arrayRes.items[0])
        assertFalse(actual = arrayRes.isUntranslatable)
    }

    fun testSaveResourceAddsNewStringPluralAndArray() {
        // Arrange
        val xmlContent = "<resources>\n</resources>"
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act
        repository.saveResource(StringResource("new_string", false, "Hello"))
        repository.saveResource(PluralsResource("new_plural", true, mapOf("one" to "1 cat")))
        repository.saveResource(StringArrayResource("new_array", false, listOf("A", "B")))

        // Assert
        val updatedText = psiFile.text
        assertTrue(
            actual = updatedText.contains("""<string name="new_string">Hello</string>"""),
            message = "Should contain the new string"
        )
        assertTrue(
            actual = updatedText.contains("""<plurals name="new_plural" translatable="false">"""),
            message = "Should contain the new plural with untranslatable attribute"
        )
        assertTrue(
            actual = updatedText.contains("""<item quantity="one">1 cat</item>"""),
            message = "Should contain the plural item"
        )
        assertTrue(
            actual = updatedText.contains("""<string-array name="new_array">"""),
            message = "Should contain the new array"
        )
    }

    fun testSaveResourceUpdatesExistingTag() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="welcome">Old Welcome</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act
        repository.saveResource(StringResource("welcome", false, "New Welcome!"))

        // Assert
        val updatedText = psiFile.text
        assertFalse(
            actual = updatedText.contains("Old Welcome"),
            message = "The old value should be gone"
        )
        assertTrue(
            actual = updatedText.contains("""<string name="welcome">New Welcome!</string>"""),
            message = "The new value should replace the old one without duplicating the tag"
        )
    }

    fun testDeleteResourceRemovesTagCorrectly() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="to_keep">Keep me</string>
                <string name="to_delete">Delete me</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act
        repository.deleteResource("to_delete", "string")

        // Assert
        val updatedText = psiFile.text
        assertFalse(
            actual = updatedText.contains("to_delete"),
            message = "The deleted string should not exist anymore"
        )
        assertTrue(
            actual = updatedText.contains("to_keep"),
            message = "Other tags must remain intact"
        )
    }

    fun testToggleUntranslatableAddsAndRemovesAttribute() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="test_key">Hello</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act 1: Toggle ON
        repository.toggleUntranslatable("test_key", true)
        var updatedText = psiFile.text
        assertTrue(
            actual = updatedText.contains("""translatable="false""""),
            message = "The translatable='false' attribute should be added"
        )

        // Act 2: Toggle OFF
        repository.toggleUntranslatable("test_key", false)
        updatedText = psiFile.text
        assertFalse(
            actual = updatedText.contains("translatable"),
            message = "The attribute should be completely removed"
        )
    }

    fun testSaveResourceEscapesSpecialCharactersAndSupportsUnicode() {
        // Arrange
        val xmlContent = "<resources></resources>"
        val psiFile = myFixture.addFileToProject("composeResources/values/strings_escape.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act: A brutal combination of KMP multi-language characters, emojis, and XML breakers
        val nastyString = StringResource(
            key = "global_chars",
            isUntranslatable = false,
            value = "Apple & 🍎 < > \" ' \n Japanese: 日本語, Cyrillic: Привет, German: äöüß @string/fake"
        )
        repository.saveResource(nastyString)

        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Assert
        val resultingXml = psiFile.text

        // Must not contain a CDATA block under any circumstances!
        assertFalse("XML should not use CDATA blocks for standard escaping!", resultingXml.contains("<![CDATA["))

        // 1. Standard XML Entity Escaping
        assertTrue("Ampersand must be escaped as &amp;", resultingXml.contains("Apple &amp; 🍎"))
        assertTrue("Less-than must be escaped as &lt;", resultingXml.contains("&lt;"))
        assertTrue("Greater-than must be escaped as &gt;", resultingXml.contains("&gt;"))

        // 2. Quote Escaping (Important for Android/KMP resource parsers)
        assertTrue("Double quotes must be escaped", resultingXml.contains("&quot;") || resultingXml.contains("\\\""))
        assertTrue("Single quotes must be escaped", resultingXml.contains("&apos;") || resultingXml.contains("\\'"))

        // 3. UTF-8 & Internationalization (Must remain native characters, NOT unicode hex codes)
        assertTrue("Emojis must be preserved", resultingXml.contains("🍎"))
        assertTrue("Japanese characters must be preserved", resultingXml.contains("日本語"))
        assertTrue("Cyrillic characters must be preserved", resultingXml.contains("Привет"))
        assertTrue("German umlauts must be preserved", resultingXml.contains("äöüß"))

        // 4. Special Android/KMP markers (Should be written as literal text)
        assertTrue("Resource references like @string must be preserved as text", resultingXml.contains("@string/fake"))

        // 5. Line breaks
        assertTrue("Line breaks must be preserved", resultingXml.contains("\n"))
    }

    fun testSaveResourceEscapesSpecialCharactersCorrectly() {
        // Arrange
        val xmlContent = "<resources>\n</resources>"
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        val repository = XmlResourceRepositoryImpl(project, psiFile.virtualFile)

        // Act
        // Contains characters that MUST be escaped in XML: quotes, ampersand, less-than, greater-than
        val nastyString = """Hello "World" & <friends> 'test'"""
        repository.saveResource(StringResource("nasty_key", false, nastyString))

        // Assert
        val updatedText = psiFile.text

        // XmlStringUtil.escapeString should have converted them
        assertTrue(
            actual = updatedText.contains("&quot;World&quot;"),
            message = "Quotes should be escaped"
        )
        assertTrue(
            actual = updatedText.contains("&amp;"),
            message = "Ampersands should be escaped"
        )
        assertTrue(
            actual = updatedText.contains("&lt;friends&gt;"),
            message = "Brackets should be escaped"
        )
    }
}