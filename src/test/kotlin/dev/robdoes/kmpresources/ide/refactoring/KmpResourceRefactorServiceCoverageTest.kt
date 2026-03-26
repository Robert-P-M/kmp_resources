package dev.robdoes.kmpresources.ide.refactoring

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class KmpResourceRefactorServiceCoverageTest : BasePlatformTestCase() {

    fun testCoverageSameKeyReturnsEarly() = runBlocking {
        // Hits: if (oldKey == newKey) return
        val file = LightVirtualFile("test.xml", "<resources></resources>")

        // Act: Should just return immediately without doing anything
        KmpResourceRefactorService.renameKeyInModule(project, file, "string", "same", "same")
    }

    fun testCoverageModuleNullReturnsEarly() = runBlocking {
        // Hits: val module = ModuleUtilCore.findModuleForFile(xmlFile, project) ?: return
        // A LightVirtualFile is not attached to the module's file system, so finding the module returns null.
        val file = LightVirtualFile("out_of_project.xml", "<resources></resources>")

        // Act: Should return early
        KmpResourceRefactorService.renameKeyInModule(project, file, "string", "old", "new")
    }

    fun testCoverageTargetTagNullAndNotImported() = runBlocking {
        // Hits: targetTag?.setAttribute("name", newKey) (null branch)
        // Hits: val isImported = ...any... (false branch)
        // Hits: if (resourceType == "string-array") "array" else resourceType (true branch)

        val xmlContent = "<resources></resources>" // Tag is missing!
        val xmlFile = myFixture.addFileToProject("composeResources/values/strings_cov.xml", xmlContent)

        // A file WITH references but WITHOUT imports
        val ktContent = """
            package dev.robdoes.ui
            fun render() {
                val text = Res.array.missing_key
            }
        """.trimIndent()
        val ktFile = myFixture.addFileToProject("src/commonMain/kotlin/NoImport.kt", ktContent)

        // Act
        KmpResourceRefactorService.renameKeyInModule(
            project, xmlFile.virtualFile, "string-array", "missing_key", "new_key"
        )

        // Assert: Kotlin file is updated, but XML didn't crash because of the safe call (?.)
        assertTrue(ktFile.text.contains("Res.array.new_key"))
        assertFalse(xmlFile.text.contains("new_key"))
    }

    fun testCoverageImportDirectiveReplacement() = runBlocking {
        // Hits: if (it.parent is org.jetbrains.kotlin.psi.KtImportDirective) return false (true branch)
        // Hits: for (import in imports) { ... } inside body

        val xmlContent = """<resources><string name="old_item">A</string></resources>"""
        val xmlFile = myFixture.addFileToProject("composeResources/values/strings_imp.xml", xmlContent)

        // A file with a direct, single-word import (which causes it.parent to be the KtImportDirective)
        val ktContent = """
            import old_item
            
            fun render() {
                val text = old_item
            }
        """.trimIndent()
        val ktFile = myFixture.addFileToProject("src/commonMain/kotlin/WithImport.kt", ktContent)

        // Act
        KmpResourceRefactorService.renameKeyInModule(
            project, xmlFile.virtualFile, "string", "old_item", "new_item"
        )

        // Assert: Both the import statement and the usage are updated
        assertTrue(ktFile.text.contains("import new_item"))
        assertTrue(ktFile.text.contains("val text = new_item"))

        // This will now correctly return false, because "new_item" does not contain "old_item"
        assertFalse(ktFile.text.contains("old_item"))
    }
}