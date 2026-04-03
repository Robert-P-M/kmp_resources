package dev.robdoes.kmpresources.domain.usecase

/**
 * Utility object used to validate locale tags against the BCP 47 standard.
 *
 * The primary purpose of this validator is to ensure that provided locale tags conform to the BCP 47
 * language tag format. This is particularly useful in scenarios where locale tags need to adhere
 * to the correct structure, as required by KMP/Android resource directories or other localization processes.
 */
internal object LocaleFormatValidator {
    /**
     * Regular expression that validates BCP 47 language tags.
     *
     * This regex is used to ensure that a string conforms to the structure defined by the BCP 47 standard,
     * which includes a mandatory language subtag (2 to 3 letters) and optional subtags separated by hyphens.
     * Examples of valid tags include "en", "en-US", "zh-Hant", and "fr-CA".
     *
     * The pattern consists of:
     * - A mandatory language subtag: A sequence of 2 to 3 alphabetic characters.
     * - Optional subtags: Each subtag must be alphanumeric, 2 to 8 characters long, and separated by a hyphen.
     */
    private val BCP47_REGEX = "^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*$".toRegex()

    /**
     * Regular expression pattern used to validate Android folder artifact names that represent
     * locale-specific directories within the Android resource structure.
     *
     * The pattern enforces the format `<language>-r<region>`, where:
     * - `<language>` consists of 2-3 alphabetic characters (e.g., "en" for English or "fil" for Filipino).
     * - `<region>` consists of 2 alphabetic characters (e.g., "US" for the United States or "GB" for Great Britain).
     *
     * This regex ensures compatibility with the language-region formatting conventions defined
     * in the BCP 47 standard for locale identifiers.
     */
    private val ANDROID_FOLDER_ARTIFACT_REGEX = "^[a-zA-Z]{2,3}-r[a-zA-Z]{2}$".toRegex()

    fun isValid(localeTag: String): Boolean {
        if (ANDROID_FOLDER_ARTIFACT_REGEX.matches(localeTag)) {
            return false
        }

        return BCP47_REGEX.matches(localeTag)
    }

    /**
     * Determines whether the given locale tag corresponds to an Android artifact directory.
     *
     * This function checks if the provided BCP 47 language tag matches the expected naming convention
     * for Android artifacts. Android artifact directories typically include locale-specific subdirectories
     * such as "values-en", "values-es", etc., and this method uses a predefined regex to verify the format.
     *
     * @param localeTag The BCP 47 language tag (e.g., "en", "en-rUS") to be validated against the Android
     * artifact naming convention.
     * @return `true` if the locale tag matches the Android artifact directory naming convention; `false` otherwise.
     */
    fun isAndroidArtifact(localeTag: String): Boolean {
        return ANDROID_FOLDER_ARTIFACT_REGEX.matches(localeTag)
    }

}