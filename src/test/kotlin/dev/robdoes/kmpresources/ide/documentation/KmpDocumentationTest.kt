package dev.robdoes.kmpresources.ide.documentation

import com.intellij.openapi.application.runReadAction
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpDocumentationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()

        // 1. Create the default XML file
        myFixture.addFileToProject(
            "composeResources/values/strings.xml",
            """
            <resources>
                <string name="test_key">Default Value</string>
            </resources>
            """.trimIndent()
        )

        // 2. Create the German XML file
        myFixture.addFileToProject(
            "composeResources/values-de/strings.xml",
            """
            <resources>
                <string name="test_key">Deutscher Wert</string>
            </resources>
            """.trimIndent()
        )
    }

    /**
     * Helper extension to safely extract the DocumentationTarget from the sealed LinkResolveResult
     * using reflection, since the V2 API does not expose it publicly on the base interface.
     */
    private fun LinkResolveResult.extractTarget(): DocumentationTarget? {
        val method = this::class.java.methods.find { it.returnType.name.contains("DocumentationTarget") }
        if (method != null) {
            return method.invoke(this) as? DocumentationTarget
        }

        val field = this::class.java.declaredFields.find { it.type.name.contains("DocumentationTarget") }
        field?.isAccessible = true
        return field?.get(this) as? DocumentationTarget
    }

    fun testProviderReturnsDefaultTargetForValidResource() {
        // Arrange: Kotlin code with caret (<caret>) exactly on our key
        myFixture.configureByText(
            "Main.kt",
            """
            package dev.test
            val myString = Res.string.test_<caret>key
            """.trimIndent()
        )

        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = KmpDocumentationTargetProvider()

        // Act
        val target = runReadAction {
            provider.documentationTarget(elementAtCaret, elementAtCaret)
        }

        // Assert
        assertNotNull(
            actual = target,
            message = "The provider must return a valid target"
        )
        assertTrue(
            actual = target is KmpDocumentationTarget,
            message = "The target must be of type KmpDocumentationTarget"
        )

        assertEquals(
            expected = "test_key",
            actual = target.keyName
        )
        assertEquals(
            expected = "values",
            actual = target.xmlTag.containingFile.virtualFile?.parent?.name,
            message = "The initially returned target MUST originate from the 'values' (default) directory!"
        )
    }

    fun testProviderReturnsNullForInvalidElement() {
        // Arrange: Caret is positioned on an irrelevant string
        myFixture.configureByText(
            "Other.kt",
            """
            val x = "Just a normal st<caret>ring"
            """.trimIndent()
        )

        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = KmpDocumentationTargetProvider()

        // Act
        val target = runReadAction {
            provider.documentationTarget(elementAtCaret, elementAtCaret)
        }

        // Assert
        assertNull(
            actual = target,
            message = "No KMP documentation should be generated for standard strings"
        )
    }

    fun testTargetGeneratesDocumentationWithoutCrashing() {
        // Arrange
        myFixture.configureByText("Main.kt", "val myString = Res.string.test_<caret>key")
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = KmpDocumentationTargetProvider()

        val target = runReadAction {
            provider.documentationTarget(elementAtCaret, elementAtCaret) as KmpDocumentationTarget
        }

        // Act
        val docResult = runReadAction {
            target.computeDocumentation()
        }

        // Assert
        assertNotNull(
            actual = docResult,
            message = "The HTML documentation must be generated successfully"
        )
    }

    fun testLinkHandlerSuccessfullySwitchesToGermanLocale() {
        // Arrange: Retrieve the initial target (pointing to 'values')
        myFixture.configureByText("Main.kt", "val myString = Res.string.test_<caret>key")
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val provider = KmpDocumentationTargetProvider()

        val initialTarget = runReadAction {
            provider.documentationTarget(elementAtCaret, elementAtCaret) as KmpDocumentationTarget
        }
        val linkHandler = KmpDocumentationLinkHandler()

        // Act: Simulate a click on the [de] link in the popup
        val resolveResult = runReadAction {
            linkHandler.resolveLink(initialTarget, "locale_values-de")
        }

        // Assert
        assertNotNull(
            actual = resolveResult,
            message = "The LinkHandler must process the event and return a result"
        )

        // Extract the hidden target via reflection
        val newTarget = resolveResult.extractTarget() as? KmpDocumentationTarget

        assertNotNull(
            actual = newTarget,
            message = "Could not extract KmpDocumentationTarget from LinkResolveResult"
        )
        assertEquals(
            expected = "values-de",
            actual = newTarget.xmlTag.containingFile.virtualFile?.parent?.name,
            message = "The new target MUST point to the German XML file directory!"
        )
        assertEquals(
            expected = "test_key",
            actual = newTarget.keyName
        )
    }

    fun testLinkHandlerIgnoresUnknownLinks() {
        // Arrange
        myFixture.configureByText("Main.kt", "val myString = Res.string.test_<caret>key")
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val initialTarget = runReadAction {
            KmpDocumentationTargetProvider().documentationTarget(
                elementAtCaret,
                elementAtCaret
            ) as KmpDocumentationTarget
        }
        val linkHandler = KmpDocumentationLinkHandler()

        // Act
        val result = linkHandler.resolveLink(initialTarget, "some_random_link")

        // Assert
        assertNull(
            actual = result,
            message = "Unknown links must be ignored and return null"
        )
    }

    fun testLinkHandlerReturnsNullForKmpEditAction() {
        // Arrange
        myFixture.configureByText("Main.kt", "val myString = Res.string.test_<caret>key")
        val elementAtCaret = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val initialTarget = runReadAction {
            KmpDocumentationTargetProvider().documentationTarget(
                elementAtCaret,
                elementAtCaret
            ) as KmpDocumentationTarget
        }
        val linkHandler = KmpDocumentationLinkHandler()

        // Act
        val result = linkHandler.resolveLink(initialTarget, "kmp_edit")

        // Assert
        assertNull(
            actual = result,
            message = "The edit link must not generate a new target, but rather execute navigation and return null"
        )
    }
}