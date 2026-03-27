package dev.robdoes.kmpresources.core.shared

import java.util.*

data class LocaleInfo(
    val languageTag: String,
    val displayName: String,
    val flagEmoji: String
)

object LocaleProvider {

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


    private fun getFlagEmoji(countryCode: String): String {
        if (countryCode.length != 2) return ""
        val codeCaps = countryCode.uppercase()
        if (!codeCaps[0].isLetter() || !codeCaps[1].isLetter()) return ""

        val firstLetter = Character.codePointAt(codeCaps, 0) - 0x41 + 0x1F1E6
        val secondLetter = Character.codePointAt(codeCaps, 1) - 0x41 + 0x1F1E6

        return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
    }
}
