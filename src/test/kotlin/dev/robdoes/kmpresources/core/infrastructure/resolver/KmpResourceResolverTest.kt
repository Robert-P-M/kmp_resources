package dev.robdoes.kmpresources.core.infrastructure.resolver

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.ResourceType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpResourceResolverTest : BasePlatformTestCase() {

    fun testResolveReferenceExtractsStringCorrectly() {
        // Arrange: Simulate a Kotlin file with the cursor (<caret>) inside the resource call
        myFixture.configureByText(
            "MainScreen.kt",
            "val text = Res.string.lo<caret>gin_title"
        )
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act
        val resolved = KmpResourceResolver.resolveReference(elementAtCaret)

        // Assert
        assertNotNull(
            actual = resolved,
            message = "The resolver should successfully parse the Res.string reference"
        )
        assertEquals(expected = "login_title", actual = resolved.key)
        assertEquals(expected = ResourceType.String, actual = resolved.type)
        assertEquals(expected = "string", actual = resolved.xmlTag)
    }

    fun testResolveReferenceExtractsPluralAndArrayCorrectly() {
        // Arrange: Test plural
        myFixture.configureByText("TestPlural.kt", "val p = Res.plurals.my_<caret>plural")
        val pluralElement = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act & Assert Plural
        val resolvedPlural = KmpResourceResolver.resolveReference(pluralElement)
        assertNotNull(actual = resolvedPlural)
        assertEquals(expected = "my_plural", actual = resolvedPlural.key)
        assertEquals(expected = ResourceType.Plural, actual = resolvedPlural.type)
        assertEquals(expected = "plurals", actual = resolvedPlural.xmlTag)

        // Arrange: Test array
        myFixture.configureByText("TestArray.kt", "val a = Res.array.my_<caret>array")
        val arrayElement = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act & Assert Array
        val resolvedArray = KmpResourceResolver.resolveReference(arrayElement)
        assertNotNull(actual = resolvedArray)
        assertEquals(expected = "my_array", actual = resolvedArray.key)
        assertEquals(expected = ResourceType.Array, actual = resolvedArray.type)
        assertEquals(expected = "string-array", actual = resolvedArray.xmlTag)
    }

    fun testResolveReferenceReturnsNullForInvalidOrUnknownReferences() {
        // Arrange: An unsupported resource type (e.g., drawable)
        myFixture.configureByText("TestInvalid.kt", "val img = Res.drawable.my_<caret>icon")
        val invalidElement = myFixture.file.findElementAt(myFixture.caretOffset)!!

        // Act
        val resolved = KmpResourceResolver.resolveReference(invalidElement)

        // Assert
        assertNull(
            actual = resolved,
            message = "The resolver should return null for unsupported resource prefixes like Res.drawable"
        )
    }

    fun testFindXmlTagsLocatesCorrectTagWithNormalization() {
        // Arrange: Create a virtual XML file with a standard key and a key containing dots/dashes
        val xmlContent = """
            <resources>
                <string name="normal_key">Hello Normal</string>
                <string name="weird-key.with_dots">Hello Normalized</string>
            </resources>
        """.trimIndent()
        myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Act 1: Find normal key
        val normalResolved = KmpResourceResolver.ResolvedResource("normal_key", ResourceType.String)
        val normalTags = KmpResourceResolver.findXmlTags(project, normalResolved)

        // Assert 1
        assertEquals(
            expected = 1,
            actual = normalTags.size,
            message = "Should find exactly 1 XML tag for 'normal_key'"
        )
        assertEquals(expected = "Hello Normal", actual = normalTags[0].value.text)

        // Act 2: Find normalized key
        // When KMP generates the Res class, "weird-key.with_dots" becomes "weird_key_with_dots".
        // Our resolver needs to match the Kotlin identifier back to the raw XML attribute.
        val normalizedResolved = KmpResourceResolver.ResolvedResource("weird_key_with_dots", ResourceType.String)
        val normalizedTags = KmpResourceResolver.findXmlTags(project, normalizedResolved)

        // Assert 2
        assertEquals(
            expected = 1,
            actual = normalizedTags.size,
            message = "Should find exactly 1 XML tag by normalizing dashes and dots"
        )
        assertEquals(expected = "Hello Normalized", actual = normalizedTags[0].value.text)
    }

    fun testFindXmlTagsUtilizesCacheAndReactsToModifications() {
        // Arrange
        val xmlContent = """<resources><string name="cache_test">A</string></resources>"""
        myFixture.addFileToProject("composeResources/values/strings_cache.xml", xmlContent)

        val resolved = KmpResourceResolver.ResolvedResource("cache_test", ResourceType.String)

        // Act 1: Initial call (builds the cache)
        val tags1 = KmpResourceResolver.findXmlTags(project, resolved)
        assertEquals(
            expected = 1,
            actual = tags1.size,
            message = "Should find exactly one tag on initial lookup"
        )

        // Act 2: Physically modify the file via PSI to trigger a MODIFICATION_COUNT bump
        WriteCommandAction.runWriteCommandAction(project) {
            tags1.first().setAttribute("name", "cache_test_renamed")
        }

        // Act 3: Search for the OLD key again
        val tags2 = KmpResourceResolver.findXmlTags(project, resolved)

        // Assert: Because the MODIFICATION_COUNT changed due to the rename,
        // the cache was invalidated and should no longer find the old name!
        assertTrue(
            actual = tags2.isEmpty(),
            message = "Cache was not invalidated after PSI modification!"
        )
    }
}