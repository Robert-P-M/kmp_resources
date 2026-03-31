package dev.robdoes.kmpresources.domain.usecase

/**
 * Utility object that validates resource key fragments by matching them against a predefined
 * regular expression. Resource keys are typically used to identify elements in resource files,
 * such as strings, plurals, or arrays, and must conform to specific naming conventions.
 *
 * The validation ensures that the key fragment consists only of lowercase alphanumeric characters
 * and underscores, adhering to the format defined by the regular expression.
 */
internal object ResourceKeyValidator {

    private val KEY_REGEX = "^[a-z0-9_]*$".toRegex()

    /**
     * Checks whether the given key fragment matches the predefined format.
     *
     * This function validates the provided key fragment against a predefined regular expression
     * to ensure it adheres to the expected structure.
     *
     * @param keyFragment The string fragment representing a portion of a key to be validated.
     * @return `true` if the key fragment matches the expected format; `false` otherwise.
     */
    fun isValid(keyFragment: String): Boolean {
        return KEY_REGEX.matches(keyFragment)
    }
}
