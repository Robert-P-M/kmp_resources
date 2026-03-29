package dev.robdoes.kmpresources.core.shared

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocaleProviderTest {

    @Test
    fun `getAvailableLocales should return a sorted list of valid locales`() {
        // Act
        val locales = LocaleProvider.getAvailableLocales()

        // Assert
        assertTrue(
            actual = locales.isNotEmpty(),
            message = "The provider should return a non-empty list of locales"
        )

        // Verify alphabetical sorting
        val isSorted = locales.zipWithNext { a, b -> a.displayName <= b.displayName }.all { it }
        assertTrue(
            actual = isSorted,
            message = "The locales should be sorted alphabetically by their display name"
        )

        // Verify that empty languages are filtered out
        val hasEmptyLanguage = locales.any { it.languageTag.isBlank() }
        assertFalse(
            actual = hasEmptyLanguage,
            message = "Locales with blank languages should be filtered out"
        )
    }

    @Test
    fun `getAvailableLocales should generate correct flag emojis for valid country codes`() {
        // Act
        val locales = LocaleProvider.getAvailableLocales()

        // Assert: US Flag
        val usLocale = locales.find { it.languageTag == "en-US" }
        assertNotNull(
            actual = usLocale,
            message = "en-US locale should exist in the Java Locale list"
        )
        assertEquals(
            expected = "🇺🇸",
            actual = usLocale.flagEmoji,
            message = "The en-US locale should correctly generate the US flag emoji via Unicode"
        )

        // Assert: German Flag
        val deLocale = locales.find { it.languageTag == "de-DE" }
        assertNotNull(
            actual = deLocale,
            message = "de-DE locale should exist in the Java Locale list"
        )
        assertEquals(
            expected = "🇩🇪",
            actual = deLocale.flagEmoji,
            message = "The de-DE locale should correctly generate the German flag emoji via Unicode"
        )
    }

    @Test
    fun `getAvailableLocales should return empty flag for locales without a valid country code`() {
        // Act
        val locales = LocaleProvider.getAvailableLocales()

        // Assert: Just language, no country
        val plainDeLocale = locales.find { it.languageTag == "de" }
        assertNotNull(
            actual = plainDeLocale,
            message = "The 'de' locale (without country) should exist"
        )
        assertEquals(
            expected = "",
            actual = plainDeLocale.flagEmoji,
            message = "Locales without a country code should return an empty string for the flag emoji"
        )
    }
}