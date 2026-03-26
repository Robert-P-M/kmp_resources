package dev.robdoes.kmpresources.ide.linter

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class KmpDuplicateResourceKeyInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Enable the inspection for our test environment
        myFixture.enableInspections(KmpDuplicateResourceKeyInspection())
    }

    fun testDuplicateKeysAreHighlightedAndFixed() {
        // Arrange: Create an XML file with a duplicate key ("app_name" exists twice)
        val xmlContent = """
            <resources>
                <string name="app_name">My App</string>
                <string name="unique_key">Unique</string>
                <string <error descr="Duplicate resource key 'app_name' in the same file.">name="app_name"</error>>My App Duplicate</string>
            </resources>
        """.trimIndent()

        // Add the file to the project with the correct path to satisfy the inspection's path check
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Open the file in the simulated editor
        myFixture.configureFromExistingVirtualFile(psiFile.virtualFile)

        // Act & Assert 1: Check if the highlighting exactly matches the <error> tags
        myFixture.checkHighlighting(true, false, true)

        // Act 2: Execute the quick fix
        val action = myFixture.getAllQuickFixes().find { it.familyName.contains("Remove duplicate") }
        assertNotNull("The quick fix to remove the duplicate should be available", action)
        myFixture.launchAction(action!!)

        // Assert 3: Verify the duplicate tag was successfully removed
        val fixedXml = myFixture.editor.document.text
        assertEquals(
            "The second 'app_name' tag should have been removed",
            """
            <resources>
                <string name="app_name">My App</string>
                <string name="unique_key">Unique</string>
            </resources>
            """.trimIndent(),
            fixedXml
        )
    }
}