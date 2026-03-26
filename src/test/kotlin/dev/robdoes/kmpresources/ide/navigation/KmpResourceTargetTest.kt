package dev.robdoes.kmpresources.ide.navigation

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KmpResourceTargetTest : BasePlatformTestCase() {

    fun testTargetPropertiesAndPresentation() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="my_awesome_key">Some Value</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent) as XmlFile
        val xmlTag = psiFile.rootTag!!.findFirstSubTag("string")!!

        // Act
        val target = KmpResourceTarget(xmlTag, "my_awesome_key")

        // Assert: Basic PSI Properties
        assertEquals(expected = "my_awesome_key", actual = target.name, message = "Name should match the Kotlin key")
        assertEquals(expected = xmlTag, actual = target.parent, message = "Parent should be the actual XML tag")
        assertEquals(expected = xmlTag.project, actual = target.project, message = "Project should match")
        assertEquals(expected = psiFile, actual = target.containingFile, message = "Containing file should match")
        assertTrue(actual = target.isValid, message = "Target should be valid if the XML tag is valid")
        assertTrue(actual = target.canNavigate(), message = "Target should always be navigable")
        assertTrue(actual = target.canNavigateToSource(), message = "Target should be navigable to source")
        assertEquals(
            expected = target,
            actual = target.navigationElement,
            message = "Navigation element should be itself"
        )

        // Assert: Presentation (used in UI popups and search everywhere)
        val presentation = target.presentation
        assertNotNull(actual = presentation, message = "Presentation should not be null")
        assertEquals(
            expected = "my_awesome_key",
            actual = presentation.presentableText,
            message = "Presentable text should be the Kotlin key name"
        )
        assertEquals(
            expected = "strings.xml",
            actual = presentation.locationString,
            message = "Location string should be the file name"
        )
    }

    fun testNavigateOpensFileInEditorManager() {
        // Arrange
        val xmlContent = """
            <resources>
                <string name="nav_target">Nav Value</string>
            </resources>
        """.trimIndent()
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent) as XmlFile
        val xmlTag = psiFile.rootTag!!.findFirstSubTag("string")!!

        val target = KmpResourceTarget(xmlTag, "nav_target")
        val fileEditorManager = FileEditorManager.getInstance(project)

        // Act
        target.navigate(true)

        // Assert
        assertTrue(
            actual = fileEditorManager.isFileOpen(psiFile.virtualFile),
            message = "Calling navigate() should open the virtual file in the FileEditorManager"
        )
    }
}