package dev.robdoes.kmpresources.ide.navigation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class KmpGotoDeclarationHandlerCoverageTest : BasePlatformTestCase() {

    fun testCoverageNullSourceElement() {
        // Hits: if (sourceElement == null) return null (true branch)
        // We configure a dummy file just to get a valid editor instance
        myFixture.configureByText("Test.kt", "val x = 1")
        val handler = KmpGotoDeclarationHandler()

        // Act & Assert
        assertNull(
            actual = handler.getGotoDeclarationTargets(null, 0, myFixture.editor),
            message = "Should return null immediately if the sourceElement is null"
        )
    }

    fun testCoverageInvalidReference() {
        // Hits: val resolved = KmpResourceResolver.resolveReference(sourceElement) ?: return null (null branch)
        myFixture.configureByText("Test.kt", "val x = 1<caret>")
        val handler = KmpGotoDeclarationHandler()
        val nonResourceElement = myFixture.file.findElementAt(myFixture.caretOffset)

        // Act & Assert
        assertNull(
            actual = handler.getGotoDeclarationTargets(nonResourceElement, myFixture.caretOffset, myFixture.editor),
            message = "Should return null if the element under caret is not a KMP resource reference"
        )
    }

    fun testCoverageEmptyTags() {
        // Hits: if (tags.isEmpty()) return null (true branch)
        // We provide a completely valid Kotlin reference, but we DELIBERATELY DO NOT
        // create the corresponding strings.xml file in the project!
        myFixture.configureByText("MainScreen.kt", "val text = Res.string.miss<caret>ing_key")
        val handler = KmpGotoDeclarationHandler()
        val element = myFixture.file.findElementAt(myFixture.caretOffset)

        // Act & Assert
        assertNull(
            actual = handler.getGotoDeclarationTargets(element, myFixture.caretOffset, myFixture.editor),
            message = "Should return null if the reference is valid but no matching XML tag exists in the project"
        )
    }

    fun testCoverageActionText() {
        // Hits: override fun getActionText(...)
        val handler = KmpGotoDeclarationHandler()
        val expectedText = KmpResourcesBundle.message("action.goto.kmp.resource.text")

        // Act & Assert
        assertEquals(
            expected = expectedText,
            // DataContext.EMPTY_CONTEXT is a safe stub provided by the IntelliJ Platform
            actual = handler.getActionText(DataContext.EMPTY_CONTEXT),
            message = "The action text should match the defined bundle message"
        )
    }
}