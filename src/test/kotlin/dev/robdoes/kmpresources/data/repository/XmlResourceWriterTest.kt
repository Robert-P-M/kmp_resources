package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.XmlElementFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.StringResource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XmlResourceWriterTest : BasePlatformTestCase() {

    fun testCreateResourceTagSetsCorrectAttributesAndDelegatesContent() {
        // Arrange
        val factory = XmlElementFactory.getInstance(project)
        val resource = StringResource(
            key = "welcome_msg",
            isUntranslatable = false,
            values = mapOf(null to "Welcome", "de" to "Willkommen")
        )

        // Act
        val defaultTag = runReadAction {
            XmlResourceWriter.createResourceTag(factory, resource, null)
        }
        val germanTag = runReadAction {
            XmlResourceWriter.createResourceTag(factory, resource, "de")
        }

        // Assert Default Tag
        assertEquals(
            expected = "string",
            actual = defaultTag.name,
            message = "The tag name should match the xmlTag of the resource"
        )
        assertEquals(
            expected = "welcome_msg",
            actual = defaultTag.getAttributeValue("name"),
            message = "The 'name' attribute should match the resource key"
        )
        assertEquals(
            expected = "Welcome",
            actual = defaultTag.value.text,
            message = "The writer should delegate content writing to the resource model"
        )
        assertNull(
            actual = defaultTag.getAttribute("translatable"),
            message = "Translatable attribute should not be set if isUntranslatable is false"
        )

        // Assert German Tag
        assertEquals(
            expected = "Willkommen",
            actual = germanTag.value.text,
            message = "The writer should correctly pass the localeTag to the resource model"
        )
    }

    fun testCreateResourceTagAddsUntranslatableAttributeOnlyToDefaultLocale() {
        // Arrange
        val factory = XmlElementFactory.getInstance(project)
        val untranslatableResource = StringResource(
            key = "api_key",
            isUntranslatable = true,
            values = mapOf(null to "12345", "de" to "12345")
        )

        // Act
        val defaultTag = runReadAction {
            XmlResourceWriter.createResourceTag(factory, untranslatableResource, null)
        }
        val localeTag = runReadAction {
            XmlResourceWriter.createResourceTag(factory, untranslatableResource, "de")
        }

        // Assert
        assertEquals(
            expected = "false",
            actual = defaultTag.getAttributeValue("translatable"),
            message = "The translatable attribute should be 'false' for the default locale"
        )
        assertNull(
            actual = localeTag.getAttribute("translatable"),
            message = "The translatable attribute MUST NOT be set on localized tags, even if the resource is untranslatable"
        )
    }

    fun testAppendEscapedTextHandlesStandardXmlEntities() {
        // Arrange
        val factory = XmlElementFactory.getInstance(project)
        val targetTag = runReadAction { factory.createTagFromText("<string name=\"test\"/>") }
        val rawText = "A & B < C > D"

        // Act
        runReadAction {
            XmlResourceWriter.appendEscapedText(factory, targetTag, rawText)
        }

        // Assert
        // We check the raw XML text of the tag to see the actual escaped entities
        val xmlText = targetTag.text
        assertEquals(
            expected = "<string name=\"test\">A &amp; B &lt; C &gt; D</string>",
            actual = xmlText,
            message = "Standard XML entities (&, <, >) must be properly escaped"
        )
    }

    fun testAppendEscapedTextHandlesAndroidSpecificQuotes() {
        // Arrange
        val factory = XmlElementFactory.getInstance(project)
        val targetTag = runReadAction { factory.createTagFromText("<string name=\"test\"/>") }
        val rawText = "It's a test with 'single quotes'"

        // Act
        runReadAction {
            XmlResourceWriter.appendEscapedText(factory, targetTag, rawText)
        }

        // Assert
        val xmlText = targetTag.text
        assertEquals(
            expected = "<string name=\"test\">It\\'s a test with \\'single quotes\\'</string>",
            actual = xmlText,
            message = "Single quotes must be escaped with a backslash for Android/KMP resource compilers"
        )
    }

    fun testAppendEscapedTextDoesNothingForEmptyString() {
        // Arrange
        val factory = XmlElementFactory.getInstance(project)
        val targetTag = runReadAction { factory.createTagFromText("<string name=\"test\"/>") }

        // Act
        runReadAction {
            XmlResourceWriter.appendEscapedText(factory, targetTag, "")
        }

        // Assert
        assertEquals(
            expected = "<string name=\"test\"/>",
            actual = targetTag.text,
            message = "Appending an empty string should not alter the tag"
        )
    }
}