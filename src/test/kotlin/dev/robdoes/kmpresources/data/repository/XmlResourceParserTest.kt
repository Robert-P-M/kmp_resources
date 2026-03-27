package dev.robdoes.kmpresources.data.repository

import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XmlResourceParserTest : BasePlatformTestCase() {

    fun testParseSuccessfullyExtractsAllSupportedResourceTypes() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="app_name">My App</string>
                <plurals name="items_count">
                    <item quantity="one">1 item</item>
                    <item quantity="other">Many items</item>
                </plurals>
                <string-array name="options">
                    <item>First</item>
                    <item>Second</item>
                </string-array>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent) as XmlFile

        // Act
        val resources = XmlResourceParser.parse(psiFile)

        // Assert
        assertEquals(
            expected = 3,
            actual = resources.size,
            message = "Should correctly parse exactly 3 valid resources"
        )

        // Verify String
        val stringRes = resources.find { it.key == "app_name" } as? StringResource
        assertNotNull(actual = stringRes, message = "String resource 'app_name' should be parsed")
        assertEquals(expected = "My App", actual = stringRes.values[null])
        assertFalse(actual = stringRes.isUntranslatable, message = "Should be translatable by default")

        // Verify Plural
        val pluralRes = resources.find { it.key == "items_count" } as? PluralsResource
        assertNotNull(actual = pluralRes, message = "Plurals resource 'items_count' should be parsed")
        val pluralItems = pluralRes.localizedItems[null] ?: emptyMap()
        assertEquals(expected = 2, actual = pluralItems.size)
        assertEquals(expected = "1 item", actual = pluralItems["one"])
        assertEquals(expected = "Many items", actual = pluralItems["other"])

        // Verify Array
        val arrayRes = resources.find { it.key == "options" } as? StringArrayResource
        assertNotNull(actual = arrayRes, message = "String array resource 'options' should be parsed")
        val arrayItems = arrayRes.localizedItems[null] ?: emptyList()
        assertEquals(expected = 2, actual = arrayItems.size)
        assertEquals(expected = "First", actual = arrayItems[0])
        assertEquals(expected = "Second", actual = arrayItems[1])
    }

    fun testParseIdentifiesUntranslatableAttribute() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="config_key" translatable="false">12345</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent) as XmlFile

        // Act
        val resources = XmlResourceParser.parse(psiFile)

        // Assert
        val stringRes = resources.first()
        assertTrue(
            actual = stringRes.isUntranslatable,
            message = "The parser should detect translatable=\"false\" and set the flag accordingly"
        )
    }

    fun testParseIgnoresUnknownTagsAndMissingNames() {
        // Arrange
        val xmlContent = """
            <resources>
                <string>Lost String</string>
                <color name="primary">#FFFFFF</color>
                <string name="valid">I am valid</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent) as XmlFile

        // Act
        val resources = XmlResourceParser.parse(psiFile)

        // Assert
        assertEquals(
            expected = 1,
            actual = resources.size,
            message = "Should ignore tags without a name or with unsupported types"
        )
        assertEquals(
            expected = "valid",
            actual = resources.first().key,
            message = "Only the properly formatted string tag should be parsed"
        )
    }

    fun testParseHandlesEmptyTagsAndEmptyRootGracefully() {
        // Arrange: 1. Empty resources root
        val emptyXml = myFixture.addFileToProject("empty.xml", "<resources/>") as XmlFile

        // Arrange: 2. Tags with no text inside
        val noTextXml = myFixture.addFileToProject(
            "no_text.xml",
            "<resources><string name=\"empty_string\"></string></resources>"
        ) as XmlFile

        // Act & Assert
        assertTrue(
            actual = XmlResourceParser.parse(emptyXml).isEmpty(),
            message = "An empty <resources> tag should yield an empty list"
        )

        val noTextResources = XmlResourceParser.parse(noTextXml)
        assertEquals(
            expected = 1,
            actual = noTextResources.size,
            message = "A tag with an empty body should still be parsed as a valid resource"
        )
        val emptyStringRes = noTextResources.first() as StringResource
        assertEquals(
            expected = "",
            actual = emptyStringRes.values[null],
            message = "The parsed text value of an empty tag should be an empty string"
        )
    }
}