package dev.robdoes.kmpresources.core.shared

import java.util.*

/**
 * Data class representing information about a specific locale.
 *
 * This class encapsulates details necessary for locale representation,
 * including the language tag, display name, and corresponding flag emoji.
 *
 * @property languageTag The IETF BCP 47 language tag representing the locale (e.g., "en-US").
 * @property displayName The human-readable name of the locale (e.g., "English (United States)").
 * @property flagEmoji A Unicode flag emoji corresponding to the country/region of the locale (e.g., "🇺🇸").
 */
internal data class LocaleInfo(
    val languageTag: String,
    val displayName: String,
    val flagEmoji: String
)

/**
 * Provides functionality to retrieve and process locale-related information.
 *
 * This object is responsible for creating a list of available locales along with their
 * metadata, including language tag, display name, and flag emoji representation.
 */
internal object LocaleProvider {

    /**
     * Retrieves a list of available locales, filtered and formatted for display.
     *
     * This method gathers all locales provided by the JVM, filters out locales
     * with blank language codes, ensures uniqueness based on the language tag,
     * and maps them to `LocaleInfo` objects. Each `LocaleInfo` object contains
     * the language tag, display name in English, and a corresponding flag emoji
     * derived from the country code. The resulting list is sorted alphabetically
     * by the display name.
     *
     * @return A sorted list of `LocaleInfo` objects representing valid available locales.
     */
    fun getAvailableLocales(): List<LocaleInfo> {
        return Locale.getAvailableLocales()
            .asSequence()
            .filter { it.language.isNotBlank() }
            .distinctBy { it.toLanguageTag() }
            .map { locale ->
                val tag = locale.toLanguageTag()
                val name = locale.getDisplayName(Locale.ENGLISH)
                val flag = getFlagEmoji(locale.country)
                LocaleInfo(tag, name, flag)
            }
            .sortedBy { it.displayName }
            .toList()
    }

    /**
     * Converts a given two-character country code into its corresponding Unicode flag emoji.
     *
     * The method takes a two-letter ISO 3166-1 alpha-2 country code as input, validates it,
     * and generates a flag emoji using the Unicode Regional Indicator Symbol Letters.
     * If the input is invalid (not exactly two letters or contains non-alphabetic characters),
     * an empty string is returned.
     *
     * @param countryCode The ISO 3166-1 alpha-2 country code (e.g., "US" for the United States).
     * @return The Unicode flag emoji corresponding to the provided country code, or an empty string if invalid.
     */
    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val codeCaps = countryCode.uppercase()
        if (!codeCaps[0].isLetter() || !codeCaps[1].isLetter()) return ""

        val firstLetter = Character.codePointAt(codeCaps, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(codeCaps, 1) - 0x41 + 0x1F1E6

        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
