package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocaleDetectionServiceTest : BasePlatformTestCase() {

    fun testGetActiveLocalesFindsValidLocalesAndSortsThem() = runBlocking {
        // Arrange: Create files for German, French, and Spanish
        myFixture.addFileToProject("composeResources/values-de/strings.xml", "<resources/>")
        myFixture.addFileToProject("composeResources/values-fr/strings.xml", "<resources/>")
        myFixture.addFileToProject("composeResources/values-es/string.xml", "<resources/>")

        val detectionService = project.service<LocaleDetectionService>()

        // Act
        val locales = detectionService.getActiveLocales()

        // Assert
        assertEquals(
            expected = 3,
            actual = locales.size,
            message = "Should find exactly 3 valid locale directories"
        )

        // Note: Sorted alphabetically by their English display name:
        // fr (French), de (German), es (Spanish)
        assertEquals(expected = "fr", actual = locales[0].languageTag)
        assertEquals(expected = "de", actual = locales[1].languageTag)
        assertEquals(expected = "es", actual = locales[2].languageTag)
    }

    fun testGetActiveLocalesIgnoresInvalidPathsAndNames() = runBlocking {
        // Arrange: Create files that should NOT be detected

        // 1. Wrong file content (e.g. no <resources> tag, which is the new condition)
        myFixture.addFileToProject("composeResources/values-it/colors.xml", "<palette/>")
        // 2. Wrong base path (not in composeResources)
        myFixture.addFileToProject("androidApp/src/main/res/values-ru/strings.xml", "<resources/>")
        // 3. Default values folder (doesn't start with 'values-')
        myFixture.addFileToProject("composeResources/values/strings.xml", "<resources/>")

        val detectionService = project.service<LocaleDetectionService>()

        // Act
        val locales = detectionService.getActiveLocales()

        // Assert
        assertTrue(
            actual = locales.isEmpty(),
            message = "Should not detect any active locales from invalid paths or file names"
        )
    }

    fun testGetDefaultLocaleReturnsExpectedDefault() = runBlocking {
        // Arrange
        val detectionService = project.service<LocaleDetectionService>()

        // Act
        val defaultLocale = detectionService.getDefaultLocale()

        // Assert
        assertEquals(
            expected = "default",
            actual = defaultLocale.languageTag,
            message = "The language tag for the default locale must be 'default'"
        )
        assertEquals(
            expected = KmpResourcesBundle.message("doc.popup.locale.default"),
            actual = defaultLocale.displayName,
            message = "The display name should match the bundle translation"
        )
    }
}