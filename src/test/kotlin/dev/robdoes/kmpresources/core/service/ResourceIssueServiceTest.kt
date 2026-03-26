package dev.robdoes.kmpresources.core.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.DefaultAsserter.assertEquals

class ResourceIssueServiceTest : BasePlatformTestCase() {

    fun testFindAllResourceFilesFindsOnlyValidKmpStringsFiles() = runBlocking {
        // Arrange
        val validFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources></resources>")
        myFixture.addFileToProject("composeResources/values/colors.xml", "<resources></resources>")
        myFixture.addFileToProject("androidApp/src/main/res/values/strings.xml", "<resources></resources>")

        val issueService = project.service<ResourceIssueService>()

        // Act
        val files = issueService.findAllResourceFiles()

        // Assert
        assertEquals(
            expected = 1,
            actual = files.size,
            message = "Should only find strings.xml files that are inside a 'composeResources' directory"
        )
        assertEquals(
            expected = validFile.virtualFile.path,
            actual = files[0].path,
            message = "The found file should be the correct valid composeResources file"
        )
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

        myFixture.addFileToProject(
            "src/commonMain/kotlin/App.kt",
            "val text = Res.string.used_key"
        )

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
}