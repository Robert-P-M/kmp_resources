package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class KmpResourceWorkspaceServiceTest : BasePlatformTestCase() {

    fun testWorkspaceServiceCachesAndFlowsResources() = runBlocking {
        // Arrange
        val xml = """<resources><string name="test_key">Value</string></resources>"""
        val file = myFixture.addFileToProject("composeResources/values/strings.xml", xml)

        val service = project.service<KmpResourceWorkspaceService>()

        // Act
        val flow = service.getResourceStateFlow(file.virtualFile)

        service.forceReload(file.virtualFile)

        val resources = flow.value

        // Assert
        assertEquals(1, resources.size)
        assertEquals("test_key", resources.first().key)
    }
}