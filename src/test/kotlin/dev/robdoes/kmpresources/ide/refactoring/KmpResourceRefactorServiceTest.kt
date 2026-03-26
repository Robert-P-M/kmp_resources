package dev.robdoes.kmpresources.ide.refactoring

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KmpResourceRefactorServiceTest : BasePlatformTestCase() {

    fun testRenameKeyUpdatesXmlAndKotlinReferencesAndImports() = runBlocking {
        // Arrange: 1. The XML File
        val xmlContent = """
            <resources>
                <string name="old_key">Hello World</string>
            </resources>
        """.trimIndent()
        val xmlFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Arrange: 2. The Kotlin File with direct reference AND an import reference
        val kotlinContent = """
            package dev.robdoes.ui
            
            import kmpresources.generated.resources.Res
            import kmpresources.generated.resources.old_key
            
            fun render() {
                // Direct reference
                val text1 = Res.string.old_key
                // Imported reference
                val text2 = old_key
            }
        """.trimIndent()
        val kotlinFile = myFixture.addFileToProject("src/commonMain/kotlin/App.kt", kotlinContent)

        // Act
        KmpResourceRefactorService.renameKeyInModule(
            project = project,
            xmlFile = xmlFile.virtualFile,
            resourceType = "string",
            oldKey = "old_key",
            newKey = "new_key"
        )

        // Assert XML Update
        val updatedXml = xmlFile.text
        assertTrue(
            actual = updatedXml.contains("""<string name="new_key">Hello World</string>"""),
            message = "The XML file should be updated with the new key attribute"
        )
        assertFalse(
            actual = updatedXml.contains("old_key"),
            message = "The old key should no longer exist in the XML"
        )

        // Assert Kotlin References Update
        val updatedKotlin = kotlinFile.text
        assertTrue(
            actual = updatedKotlin.contains("Res.string.new_key"),
            message = "The direct Kotlin reference should be updated to the new key"
        )

        // Assert Kotlin Imports Update
        assertTrue(
            actual = updatedKotlin.contains("import kmpresources.generated.resources.new_key"),
            message = "The import directive should be correctly renamed to the new key"
        )

        assertFalse(
            actual = updatedKotlin.contains("old_key"),
            message = "The old key should be completely purged from the Kotlin file"
        )
    }
}