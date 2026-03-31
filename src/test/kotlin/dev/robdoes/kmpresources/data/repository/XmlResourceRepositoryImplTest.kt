package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.StringResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class XmlResourceRepositoryImplTest : BasePlatformTestCase() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runSuspendTest(block: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()

        project.service<KmpProjectScopeService>().coroutineScope.launch {
            try {
                deferred.complete(block())
            } catch (e: Throwable) {
                deferred.completeExceptionally(e)
            }
        }

        while (!deferred.isCompleted) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(1)
        }

        return deferred.getCompleted()
    }

    override fun setUp() {
        super.setUp()
        // Setup initial XML files before each test
        myFixture.addFileToProject(
            "composeResources/values/strings.xml",
            """
            <resources>
                <string name="test_key">Default Value</string>
                <string name="delete_me">To be deleted</string>
            </resources>
            """.trimIndent()
        )

        myFixture.addFileToProject(
            "composeResources/values-de/strings.xml",
            """
            <resources>
                <string name="test_key">Deutscher Wert</string>
                <string name="delete_me">Wird geloescht</string>
            </resources>
            """.trimIndent()
        )
    }

    private fun getRepository(): XmlResourceRepositoryImpl {
        val defaultFile = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
        return XmlResourceRepositoryImpl(project, defaultFile)
    }

    fun testParseResourcesFromDiskMergesDefaultAndLocales() = runSuspendTest {
        // Arrange
        val repository = getRepository()

        // Act
        val resources = repository.parseResourcesFromDisk()

        // Assert
        assertEquals(
            expected = 2,
            actual = resources.size,
            message = "Should parse exactly two distinct keys"
        )

        val testResource = resources.find { it.key == "test_key" } as? StringResource
        assertNotNull("test_key should be parsed as a StringResource", testResource)

        assertEquals(
            expected = "Default Value",
            actual = testResource!!.values[null],
            message = "Default value should be correctly parsed"
        )
        assertEquals(
            expected = "Deutscher Wert",
            actual = testResource.values["de"],
            message = "German locale value should be correctly merged into the resource"
        )
    }

    fun testSaveResourceAddsNewStringAcrossLocales() = runSuspendTest {
        // Arrange
        val repository = getRepository()
        val newResource = StringResource(
            key = "new_key",
            isUntranslatable = false,
            values = mapOf(
                null to "New Default",
                "de" to "Neu Deutsch"
            )
        )

        // Act
        repository.saveResource(newResource)

        // Assert
        val defaultFile = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
        val deFile = myFixture.findFileInTempDir("composeResources/values-de/strings.xml")!!

        val defaultContent = String(defaultFile.contentsToByteArray(), Charsets.UTF_8)
        val deContent = String(deFile.contentsToByteArray(), Charsets.UTF_8)

        assertTrue(
            actual = defaultContent.contains("""<string name="new_key">New Default</string>"""),
            message = "Default file should contain the newly saved string"
        )
        assertTrue(
            actual = deContent.contains("""<string name="new_key">Neu Deutsch</string>"""),
            message = "German file should contain the newly saved localized string"
        )
    }

    fun testDeleteResourceRemovesKeyFromAllLocales() = runSuspendTest {
        // Arrange
        val repository = getRepository()

        // Act
        repository.deleteResource("delete_me", ResourceType.String)

        // Assert
        val defaultFile = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
        val deFile = myFixture.findFileInTempDir("composeResources/values-de/strings.xml")!!

        val defaultContent = String(defaultFile.contentsToByteArray(), Charsets.UTF_8)
        val deContent = String(deFile.contentsToByteArray(), Charsets.UTF_8)

        assertFalse(
            actual = defaultContent.contains("delete_me"),
            message = "The key 'delete_me' should be removed from the default file"
        )
        assertFalse(
            actual = deContent.contains("delete_me"),
            message = "The key 'delete_me' should be removed from the German file"
        )
        assertTrue(
            actual = defaultContent.contains("test_key"),
            message = "Other keys should remain untouched"
        )
    }

    fun testToggleUntranslatableAddsAttributeAndDeletesTranslations() = runSuspendTest {
        // Arrange
        val repository = getRepository()

        // Act
        // We set 'test_key' to untranslatable = true
        repository.toggleUntranslatable("test_key", true)

        // Assert
        val defaultFile = myFixture.findFileInTempDir("composeResources/values/strings.xml")!!
        val deFile = myFixture.findFileInTempDir("composeResources/values-de/strings.xml")!!

        val defaultContent = String(defaultFile.contentsToByteArray(), Charsets.UTF_8)
        val deContent = String(deFile.contentsToByteArray(), Charsets.UTF_8)

        // 1. Check if the translatable attribute was added to the default file
        assertTrue(
            actual = defaultContent.contains("""<string name="test_key" translatable="false">Default Value</string>"""),
            message = "Default file should now have translatable='false' attribute"
        )

        // 2. Check if the translation was deleted from the German file
        assertFalse(
            actual = deContent.contains("test_key"),
            message = "The localized 'test_key' MUST be deleted from the German file when marked as untranslatable"
        )
    }
}