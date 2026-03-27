package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.StringResource
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class XmlResourceRepositoryImplTest : BasePlatformTestCase() {

    fun testLoadResourcesMergesDefaultAndLocaleFilesCorrectly() {
        // Arrange
        val defaultXml = """<resources><string name="greeting">Hello</string></resources>"""
        val germanXml = """<resources><string name="greeting">Hallo</string></resources>"""

        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", defaultXml)
        myFixture.addFileToProject("composeResources/values-de/strings.xml", germanXml)

        val repository = XmlResourceRepositoryImpl(project, defaultFile.virtualFile)

        // Act
        val resources = repository.loadResources()

        // Assert
        assertEquals(
            expected = 1,
            actual = resources.size,
            message = "Should load and merge the 'greeting' key into a single XmlResource object"
        )

        val stringRes = resources.first() as StringResource
        assertEquals(expected = "Hello", actual = stringRes.values[null])
        assertEquals(expected = "Hallo", actual = stringRes.values["de"])
    }

    fun testSaveResourceWritesToMultipleLocaleFiles() = runBlocking {
        // Arrange
        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>")
        val repository = XmlResourceRepositoryImpl(project, defaultFile.virtualFile)

        val newResource = StringResource(
            key = "farewell",
            isUntranslatable = false,
            values = mapOf(null to "Goodbye", "es" to "Adios", "fr" to "Au revoir")
        )

        // Act
        repository.saveResource(newResource)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Assert: Check PSI structure instead of raw text
        runReadAction {
            // Default file
            val defaultXml = PsiManager.getInstance(project).findFile(defaultFile.virtualFile) as XmlFile
            val defaultTag = defaultXml.rootTag?.subTags?.find { it.getAttributeValue("name") == "farewell" }
            assertNotNull(actual = defaultTag, message = "Should create the tag in the default strings.xml")
            assertEquals(expected = "Goodbye", actual = defaultTag.value.text)

            // Spanish file (should have been created automatically)
            val esFile = defaultFile.virtualFile.parent.parent.findChild("values-es")?.findChild("strings.xml")
            assertNotNull(actual = esFile, message = "Should automatically create values-es/strings.xml")
            val esXml = PsiManager.getInstance(project).findFile(esFile) as XmlFile
            val esTag = esXml.rootTag?.subTags?.find { it.getAttributeValue("name") == "farewell" }
            assertEquals(expected = "Adios", actual = esTag?.value?.text)

            // French file
            val frFile = defaultFile.virtualFile.parent.parent.findChild("values-fr")?.findChild("strings.xml")
            val frXml = PsiManager.getInstance(project).findFile(frFile!!) as XmlFile
            val frTag = frXml.rootTag?.subTags?.find { it.getAttributeValue("name") == "farewell" }
            assertEquals(expected = "Au revoir", actual = frTag?.value?.text)
        }
    }

    fun testDeleteResourceRemovesTagFromAllLocaleFiles() {
        // Arrange
        val defaultXml = """<resources><string name="to_delete">Delete Me</string></resources>"""
        val germanXml = """<resources><string name="to_delete">Lösch Mich</string></resources>"""

        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", defaultXml)
        val germanFile = myFixture.addFileToProject("composeResources/values-de/strings.xml", germanXml)

        val repository = XmlResourceRepositoryImpl(project, defaultFile.virtualFile)

        // Act
        repository.deleteResource("to_delete", ResourceType.String)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Assert
        runReadAction {
            val defXmlFile = PsiManager.getInstance(project).findFile(defaultFile.virtualFile) as XmlFile
            val deXmlFile = PsiManager.getInstance(project).findFile(germanFile.virtualFile) as XmlFile

            assertNull(
                actual = defXmlFile.rootTag?.subTags?.find { it.getAttributeValue("name") == "to_delete" },
                message = "The tag should be deleted from the default file"
            )
            assertNull(
                actual = deXmlFile.rootTag?.subTags?.find { it.getAttributeValue("name") == "to_delete" },
                message = "The tag should be deleted from the localized file"
            )
        }
    }

    fun testToggleUntranslatableAddsAttributeAndCleansUpTranslations() {
        // Arrange
        val defaultXml = """<resources><string name="api_key">12345</string></resources>"""
        val germanXml = """<resources><string name="api_key">12345_DE</string></resources>""" // Translation that shouldn't exist

        val defaultFile = myFixture.addFileToProject("composeResources/values/strings.xml", defaultXml)
        val germanFile = myFixture.addFileToProject("composeResources/values-de/strings.xml", germanXml)

        val repository = XmlResourceRepositoryImpl(project, defaultFile.virtualFile)

        // Act: Set to untranslatable = true
        repository.toggleUntranslatable("api_key", true)
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        // Assert
        runReadAction {
            val defXmlFile = PsiManager.getInstance(project).findFile(defaultFile.virtualFile) as XmlFile
            val defTag = defXmlFile.rootTag?.subTags?.find { it.getAttributeValue("name") == "api_key" }!!

            assertEquals(
                expected = "false",
                actual = defTag.getAttributeValue("translatable"),
                message = "The default tag should now have translatable='false'"
            )

            val deXmlFile = PsiManager.getInstance(project).findFile(germanFile.virtualFile) as XmlFile
            val deTag = deXmlFile.rootTag?.subTags?.find { it.getAttributeValue("name") == "api_key" }
            assertNull(
                actual = deTag,
                message = "Setting a key to untranslatable MUST delete all its existing translations in locale files"
            )
        }
    }
}