package dev.robdoes.kmpresources.domain.usecase

object ResourceKeyValidator {

    private val KEY_REGEX = "^[a-z0-9_]*$".toRegex()

    fun isValid(keyFragment: String): Boolean {
        return KEY_REGEX.matches(keyFragment)
    }
}
