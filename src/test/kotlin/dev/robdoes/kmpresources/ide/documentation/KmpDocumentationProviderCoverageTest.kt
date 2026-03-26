package dev.robdoes.kmpresources.ide.documentation

import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.ide.navigation.KmpResourceTarget
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KmpDocumentationProviderCoverageTest : BasePlatformTestCase() {

    fun testCoverageNullsAndEarlyReturns() {
        // FIX: First configure a dummy file to initialize myFixture.editor!
        myFixture.configureByText("Test.kt", "val x = 1<caret>")

        val provider = KmpDocumentationProvider()

        // Hits: resolveReference(contextElement ?: return null)
        assertNull(
            actual = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, null, 0),
            message = "Should return null if contextElement is null"
        )

        val nonResourceElement = myFixture.file.findElementAt(myFixture.caretOffset)

        // Hits: resolveReference returns null
        assertNull(
            actual = provider.getCustomDocumentationElement(myFixture.editor, myFixture.file, nonResourceElement, 0),
            message = "Should return null if element under caret is not a KMP resource reference"
        )

        // Hits: if (element !is KmpResourceTarget) return null
        assertNull(
            actual = provider.generateDoc(nonResourceElement, null),
            message = "Should return null if target element is not a KmpResourceTarget"
        )
    }

    fun testCoverageStringArrayAndLocales() {
        val xmlDefault = """
            <resources>
                <string-array name="my_array">
                    <item>Default Item</item>
                </string-array>
            </resources>
        """.trimIndent()

        val xmlDe = """
            <resources>
                <string-array name="my_array">
                    <item>Deutsches Item</item>
                </string-array>
            </resources>
        """.trimIndent()

        myFixture.addFileToProject("composeResources/values/strings.xml", xmlDefault)
        val dePsiFile = myFixture.addFileToProject("composeResources/values-de/strings.xml", xmlDe) as XmlFile

        val deTag = dePsiFile.rootTag!!.findFirstSubTag("string-array")!!
        val target = KmpResourceTarget(deTag, "my_array")

        val doc = KmpDocumentationProvider().generateDoc(target, null)
        assertNotNull(actual = doc, message = "Generated documentation should not be null")

        assertTrue(actual = doc.contains("<ol>"), message = "Array documentation should contain an ordered list <ol>")
        assertTrue(actual = doc.contains("<li>Deutsches Item</li>"), message = "Array items should be listed as <li>")
        assertTrue(actual = doc.contains("<i>de</i>"), message = "The locale 'de' should be indicated in the header")
        assertTrue(
            actual = doc.contains("[default]"),
            message = "A translation link to the default locale should be present"
        )
    }

    fun testCoverageFindAvailableLocalesEarlyReturns() {
        val xmlPsi =
            myFixture.addFileToProject("strings.xml", "<resources><string name=\"x\">y</string></resources>") as XmlFile
        val tag = xmlPsi.rootTag!!.findFirstSubTag("string")!!
        val target = KmpResourceTarget(tag, "x")

        val doc = KmpDocumentationProvider().generateDoc(target, null)
        assertNotNull(actual = doc, message = "Generated documentation should not be null")
        assertTrue(
            actual = !doc.contains("Translations:"),
            message = "Should not contain translations section since the file is not in a proper values directory"
        )
    }

    fun testCoverageLinks() {
        val xmlDefault = """<resources><string name="loc_key">Hello</string></resources>"""
        val xmlPsi = myFixture.addFileToProject("composeResources/values/strings.xml", xmlDefault) as XmlFile
        val tag = xmlPsi.rootTag!!.findFirstSubTag("string")!!
        val target = KmpResourceTarget(tag, "loc_key")

        val provider = KmpDocumentationProvider()
        val psiManager = PsiManager.getInstance(project)

        assertNull(
            actual = provider.getDocumentationElementForLink(psiManager, null, target),
            message = "Should return null if the link is null"
        )
        assertNull(
            actual = provider.getDocumentationElementForLink(psiManager, "kmp_edit", null),
            message = "Should return null if the context element is null"
        )
        assertNull(
            actual = provider.getDocumentationElementForLink(psiManager, "kmp_edit", target),
            message = "The kmp_edit link should invoke navigation and return null for the doc popup"
        )
        assertNull(
            actual = provider.getDocumentationElementForLink(psiManager, "locale_/invalid/path.xml", target),
            message = "Should return null if the locale file path cannot be resolved"
        )

        // Using URL here ensures compatibility with the TempFileSystem used in our tests
        val validLink = "locale_${xmlPsi.virtualFile.url}"
        val resolvedTarget = provider.getDocumentationElementForLink(psiManager, validLink, target)

        assertNotNull(
            actual = resolvedTarget,
            message = "A valid locale link should be successfully resolved"
        )
        assertTrue(
            actual = resolvedTarget is KmpResourceTarget,
            message = "The resolved target should be instantiated as a KmpResourceTarget"
        )

        assertNull(
            actual = provider.getDocumentationElementForLink(psiManager, "unknown_link_type", target),
            message = "Unknown link prefixes should fall through and return null"
        )
    }
}