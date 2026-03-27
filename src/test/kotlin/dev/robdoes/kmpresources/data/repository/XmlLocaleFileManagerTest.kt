package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XmlLocaleFileManagerTest : BasePlatformTestCase() {

    fun testFindRelatedLocaleFilesFindsExistingTranslations() {
        // Arrange: Create a default file and two locale-specific files
        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>").virtualFile
        myFixture.addFileToProject("composeResources/values-de/strings.xml", "<resources/>")
        myFixture.addFileToProject("composeResources/values-es/strings.xml", "<resources/>")

        // Add a file that should be ignored (wrong name)
        myFixture.addFileToProject("composeResources/values-fr/colors.xml", "<resources/>")

        // Act
        val relatedFiles = runReadAction {
            XmlLocaleFileManager.findRelatedLocaleFiles(project, defaultFile)
        }

        // Assert
        assertEquals(
            expected = 2,
            actual = relatedFiles.size,
            message = "Should find exactly 2 related files (de and es), ignoring the wrong file name in fr"
        )
        assertTrue(
            actual = relatedFiles.containsKey("de"),
            message = "The 'de' locale should be found"
        )
        assertTrue(
            actual = relatedFiles.containsKey("es"),
            message = "The 'es' locale should be found"
        )
        assertTrue(
            actual = relatedFiles["de"] is XmlFile,
            message = "The returned file must be parsed as an XmlFile"
        )
    }

    fun testCreateLocaleFileInternalCreatesNewDirectoryAndFile()  {
        // Arrange
        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>").virtualFile
        val targetLocale = "it"

        // Act
        val newFile = XmlLocaleFileManager.createLocaleFileInternal(defaultFile, targetLocale)

        // Assert
        assertNotNull(
            actual = newFile,
            message = "The created file should not be null"
        )
        assertEquals(
            expected = "strings.xml",
            actual = newFile.name,
            message = "The new file should have the exact same name as the default file"
        )
        assertEquals(
            expected = "values-it",
            actual = newFile.parent.name,
            message = "The new file should be placed inside the correct values-{locale} directory"
        )

        // Verify the default content was written
        val content = String(newFile.contentsToByteArray(), Charsets.UTF_8)
        assertEquals(
            expected = "<resources>\n</resources>",
            actual = content,
            message = "A freshly created locale file should contain the default XML resources tag"
        )
    }

    fun testCreateLocaleFileInternalDoesNotOverwriteExistingContent()  {
        // Arrange
        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>").virtualFile
        val existingContent = """<resources><string name="test">Ciao</string></resources>"""

        // Pre-create the target file with some content
        myFixture.addFileToProject("composeResources/values-it/strings.xml", existingContent)

        // Act
        val existingFile = XmlLocaleFileManager.createLocaleFileInternal(defaultFile, "it")

        // Assert
        assertNotNull(
            actual = existingFile,
            message = "Should return the existing file without crashing"
        )

        val content = String(existingFile.contentsToByteArray(), Charsets.UTF_8)
        assertEquals(
            expected = existingContent,
            actual = content,
            message = "The manager MUST NOT overwrite a file if it already has content"
        )
    }

    fun testFindRelatedLocaleFilesReturnsEmptyMapForInvalidPaths() {
        // Arrange: File is not in a "values" directory
        val invalidFile = myFixture.addFileToProject("src/commonMain/kotlin/Test.kt", "").virtualFile

        // Act
        val relatedFiles = runReadAction {
            XmlLocaleFileManager.findRelatedLocaleFiles(project, invalidFile)
        }

        // Assert
        assertTrue(
            actual = relatedFiles.isEmpty(),
            message = "Should return an empty map if the base file is not inside a proper resources directory structure"
        )
    }
}