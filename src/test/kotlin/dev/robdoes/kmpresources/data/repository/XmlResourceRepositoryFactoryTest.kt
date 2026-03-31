package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class XmlResourceRepositoryFactoryTest : BasePlatformTestCase() {

    fun testFactoryIsRegisteredAsProjectServiceAndCreatesRepository() {
        // Arrange
        val xmlContent = "<resources></resources>"
        val psiFile = myFixture.addFileToProject("composeResources/values/strings.xml", xmlContent)

        // Act
        // This tests if IntelliJ correctly registered the class via the @Service annotation
        val factory = project.service<XmlResourceRepositoryFactory>()
        val repository = factory.create(psiFile.virtualFile)

        // Assert
        assertNotNull(
            actual = factory,
            message = "The factory should be successfully injected as a project service"
        )
        assertTrue(
            actual = repository is XmlResourceRepositoryImpl,
            message = "The factory should create an instance of XmlResourceRepositoryImpl"
        )
    }
}