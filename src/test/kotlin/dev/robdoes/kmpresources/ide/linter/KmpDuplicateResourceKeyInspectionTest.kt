package dev.robdoes.kmpresources.ide.linter

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class KmpDuplicateResourceKeyInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Enable the inspection for our test environment
        myFixture.enableInspections(KmpDuplicateResourceKeyInspection::class.java)
    }

    fun testDuplicateKeysAreHighlightedAndFixed() {
        // Arrange: Create an XML file with a duplicate key
        // We use the <error> marker to tell the IntelliJ test runner exactly where we expect the highlight
        val xmlContent = """
            <resources>
                <string name="app_name">My App</string>
                <string name="unique_key">Unique</string>
                <string <error descr="Duplicate resource key 'app_name' in the same file.">name="app_name"</error>>My App Duplicate</string>
            </resources>
        """.trimIndent()

        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        // Act & Assert 1: Check if the highlighting exactly matches the <error> tags
        myFixture.checkHighlighting(true, false, true)

        // Act 2: Execute the quick fix
        val action = myFixture.getAllQuickFixes().find { it.familyName.contains("Remove duplicate") }

        assertNotNull(
            actual = action,
            message = "The quick fix to remove the duplicate should be available"
        )
        myFixture.launchAction(action)

        // Assert 3: Verify the duplicate tag was successfully removed
        val expectedXml = """
            <resources>
                <string name="app_name">My App</string>
                <string name="unique_key">Unique</string>
            </resources>
        """.trimIndent()

        assertEquals(
            expected = expectedXml,
            actual = myFixture.editor.document.text.trim(),
            message = "The second 'app_name' tag should have been removed by the quick fix"
        )
    }

    fun testInspectionIgnoresValidFilesWithoutDuplicates() {
        // Arrange: A perfectly valid file
        val xmlContent = """
            <resources>
                <string name="app_name">My App</string>
                <string name="login_button">Login</string>
                <string-array name="options">
                    <item>A</item>
                </string-array>
            </resources>
        """.trimIndent()

        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        // Act & Assert: This will throw an error if ANY inspection highlight is found,
        // because we didn't put any <error> tags in the xmlContent above.
        myFixture.checkHighlighting(true, false, true)

        val fixes = myFixture.getAllQuickFixes()
        assertTrue(
            actual = fixes.isEmpty(),
            message = "No quick fixes should be available for a file without duplicates"
        )
    }

    fun testInspectionRunsOnAndroidResFilesWithDuplicates() {
        val xmlContent = """
        <resources>
            <string name="app_name">My App</string>
            <string <error descr="Duplicate resource key 'app_name' in the same file.">name="app_name"</error>>My App Duplicate</string>
        </resources>
    """.trimIndent()

        val psiFile = myFixture.addFileToProject("androidApp/src/main/res/values/strings.xml", xmlContent)
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        myFixture.checkHighlighting(true, false, true)
    }
}