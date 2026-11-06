package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.shared.Bcp47FolderMapper
import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class XmlLocaleRepositoryTest : BasePlatformTestCase() {

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> runSuspendTest(block: suspend () -> T): T {
        val deferred = CompletableDeferred<T>()

        project.service<KmpProjectScopeService>().coroutineScope.launch {
            try {
                deferred.complete(block())
            } catch (t: Throwable) {
                deferred.completeExceptionally(t)
            }
        }

        while (!deferred.isCompleted) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(1)
        }

        return deferred.getCompleted()
    }

    fun testFindAllDefaultLocaleContextsFindsStringsXmlForComposeMultiplatform() = runBlocking {
        myFixture.addFileToProject(
            "composeResources/values/strings.xml",
            """
            <resources>
                <string name="hello">Hello</string>
            </resources>
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "composeResources/values/string.xml",
            """
            <resources>
                <string name="legacy">Legacy</string>
            </resources>
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "composeResources/values/colors.xml",
            """
            <resources>
                <color name="brand">#FFFFFF</color>
            </resources>
            """.trimIndent()
        )

        val repository = XmlLocaleRepository(project)

        val contexts = repository.findAllDefaultLocaleContexts()

        assertEquals(
            expected = 2,
            actual = contexts.size,
            message = "Compose resources should keep supporting strings.xml and legacy string.xml only."
        )
        assertTrue(contexts.any { it.defaultStringsFilePath.endsWith("composeResources/values/strings.xml") })
        assertTrue(contexts.any { it.defaultStringsFilePath.endsWith("composeResources/values/string.xml") })
    }

    fun testFindAllDefaultLocaleContextsFindsOnlyStringsXmlForAndroid() = runBlocking {
        myFixture.addFileToProject(
            "androidApp/src/main/res/values/strings.xml",
            """
            <resources>
                <string name="hello">Hello</string>
                <plurals name="songs">
                    <item quantity="one">%d song</item>
                    <item quantity="other">%d songs</item>
                </plurals>
                <string-array name="planets">
                    <item>Mercury</item>
                </string-array>
            </resources>
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "androidApp/src/main/res/values/colors.xml",
            """
            <resources>
                <color name="brand">#FFFFFF</color>
            </resources>
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "androidApp/src/main/res/values/splash.xml",
            """
            <resources>
                <item name="windowSplashScreenBackground">#000000</item>
            </resources>
            """.trimIndent()
        )

        val repository = XmlLocaleRepository(project)

        val contexts = repository.findAllDefaultLocaleContexts()

        assertEquals(
            expected = 1,
            actual = contexts.size,
            message = "Android resources should expose only strings.xml as default locale source."
        )
        assertTrue(
            actual = contexts.single().defaultStringsFilePath.endsWith("androidApp/src/main/res/values/strings.xml"),
            message = "Only android strings.xml should be used as the locale source file."
        )
    }

    fun testCreateLocaleStructureCreatesStringsXmlOnlyForAndroidContext() = runSuspendTest {
        val defaultFile = myFixture.addFileToProject(
            "androidApp/src/main/res/values/strings.xml",
            """
        <resources>
            <string name="hello">Hello</string>
        </resources>
        """.trimIndent()
        )
        myFixture.addFileToProject(
            "androidApp/src/main/res/values/colors.xml",
            """
        <resources>
            <color name="brand">#FFFFFF</color>
        </resources>
        """.trimIndent()
        )

        val repository = XmlLocaleRepository(project)

        val context = AddLocaleUseCase.LocaleContext(
            defaultValuesDirPath = defaultFile.virtualFile.parent.url,
            defaultStringsFilePath = defaultFile.virtualFile.url
        )

        repository.createLocaleStructure(context, "de")

        val createdDirName = Bcp47FolderMapper.bcp47ToFolderName("de")
        val valuesDeDir = defaultFile.virtualFile.parent.parent?.findChild(createdDirName)
        val stringsFile = valuesDeDir?.findChild("strings.xml")
        val colorsFile = valuesDeDir?.findChild("colors.xml")

        assertNotNull(stringsFile, "The localized Android strings.xml should be created.")
        assertEquals(
            expected = null,
            actual = colorsFile,
            message = "No unrelated Android XML file should be created in the localized values folder."
        )
    }
}