package dev.robdoes.kmpresources.domain.usecase

/**
 * Domain rule: KMP XML resource keys (Android style) should only contain
 * lowercase letters, numbers, and underscores to be safely generated
 * into Kotlin accessors without syntax errors.
 */
object ResourceKeyValidator {

    private val KEY_REGEX = "^[a-z0-9_]*$".toRegex()

    fun isValid(keyFragment: String): Boolean {
        return KEY_REGEX.matches(keyFragment)
    }
}
