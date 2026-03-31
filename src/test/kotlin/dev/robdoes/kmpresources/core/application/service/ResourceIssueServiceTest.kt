package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

internal class ResourceIssueServiceTest : BasePlatformTestCase() {

    fun testFindAllResourceFilesFindsOnlyValidKmpStringsFiles() = runBlocking {
        // Arrange
        val validFile1 = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources></resources>")
        val validFile2 = myFixture.addFileToProject("composeResources/values/colors.xml", "<resources></resources>")
        myFixture.addFileToProject("androidApp/src/main/res/values/strings.xml", "<resources></resources>")

        val issueService = project.service<ResourceIssueService>()

        // Act
        val files = issueService.findAllResourceFiles()

        // Assert
        assertEquals(
            expected = 2,
            actual = files.size,
            message = "Should find all XML files with <resources> tag inside composeResources/values"
        )
        // Check if both valid files are found (order might vary, so we check if the paths exist)
        val filePaths = files.map { it.path }
        assertContainsElements(filePaths, validFile1.virtualFile.path, validFile2.virtualFile.path)
    }

    fun testCountIssuesCorrectlyIdentifiesUnusedKeys() = runBlocking {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="used_key">Used</string>
                <string name="unused_key">Unused</string>
                <string name="another_unused">Also unused</string>
            </resources>
        """.trimIndent()

        val xmlFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)
        myFixture.addFileToProject("src/commonMain/kotlin/App.kt", "val text = Res.string.used_key")

        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val issueService = project.service<ResourceIssueService>()

        // Act
        val issueCount = issueService.countIssues(xmlFile.virtualFile)

        // Assert
        assertEquals(
            expected = 2,
            actual = issueCount,
            message = "Should find exactly 2 unused keys (issues) out of the 3 defined in XML"
        )
    }

    fun testCountIssuesReturnsZeroForInvalidFile() = runBlocking {
        // Arrange
        val xmlFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources></resources>")
        val issueService = project.service<ResourceIssueService>()

        runWriteAction {
            xmlFile.virtualFile.delete(this)
        }

        // Act
        val issueCount = issueService.countIssues(xmlFile.virtualFile)

        // Assert
        assertEquals(
            expected = 0,
            actual = issueCount,
            message = "Should return 0 immediately if the VirtualFile is no longer valid"
        )
    }
}