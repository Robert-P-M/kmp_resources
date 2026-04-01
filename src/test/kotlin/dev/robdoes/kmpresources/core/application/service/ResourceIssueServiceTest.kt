package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals

internal class ResourceIssueServiceTest : BasePlatformTestCase() {

    fun testFindAllResourceFilesFindsOnlyValidKmpStringsFiles() = runBlocking {
        val validFile1 = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources></resources>")
        myFixture.addFileToProject("composeResources/values/colors.xml", "<resources></resources>")
        myFixture.addFileToProject("androidApp/src/main/res/values/strings.xml", "<resources></resources>")

        val issueService = project.service<ResourceIssueService>()
        val files = issueService.findAllResourceFiles()

        assertEquals(
            expected = 2,
            actual = files.size,
            message = "Should find only strings.xml inside composeResources/values — colors.xml and android paths are excluded"
        )
        assertContainsElements(files.map { it.path }, validFile1.virtualFile.path)
    }

    fun testCountIssuesCorrectlyIdentifiesUnusedKeys() = runBlocking {
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
        val issueCount = issueService.countIssues(xmlFile.virtualFile)

        assertEquals(
            expected = 2,
            actual = issueCount,
            message = "Should find exactly 2 unused keys out of 3 defined in XML"
        )
    }

    fun testCountIssuesReturnsZeroForInvalidFile() = runBlocking {
        val xmlFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources></resources>")
        val issueService = project.service<ResourceIssueService>()

        runWriteAction { xmlFile.virtualFile.delete(this) }

        val issueCount = issueService.countIssues(xmlFile.virtualFile)

        assertEquals(
            expected = 0,
            actual = issueCount,
            message = "Should return 0 if the VirtualFile is no longer valid"
        )
    }
}