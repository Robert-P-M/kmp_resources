package dev.robdoes.kmpresources.ide.linter

/**
 * Utility object that analyzes format strings and determines the number of arguments required based on
 * their format spec*/
internal object FormatArgumentAnalyzer {

    /**
     * Regular expression pattern used to match format specifiers within strings.
     *
     * Format specifiers include optional positional indices followed by a type specifier (`s` for strings
     * or `d` for digits). The regex ensures these specifiers are not preceded by a `%` character,
     * which would indicate an escaped literal `%`.
     *
     * Examples of matched patterns:
     * - `%s`: Matches a string specifier.
     * - `%d`: Matches a digit specifier.
     * - `%1$s`: Matches a positional string specifier with index 1.
     * - `%2$d`: Matches a positional digit specifier with index 2.
     */
    private val FORMAT_REGEX = """(?<!%)%([1-9]\d*\$)?(?:s|d)""".toRegex()

    /**
     * Counts the number of required arguments in a given formatted string.
     *
     * @param text the formatted string to analyze for required arguments.
     * @return the count of required arguments, determined by the highest positional index
     *         or the count of unnumbered placeholders in the string.
     */
    fun countRequiredArguments(text: String): Int {
        val matches = FORMAT_REGEX.findAll(text).toList()
        if (matches.isEmpty()) return 0

        var maxPositionalIndex = 0
        var unnumberedCount = 0

        for (match in matches) {
            val positionalGroup = match.groups[1]?.value

            if (positionalGroup != null) {
                val index = positionalGroup.dropLast(1).toIntOrNull() ?: 0
                if (index > maxPositionalIndex) {
                    maxPositionalIndex = index
                }
            } else {
                unnumberedCount++
            }
        }

        return maxOf(maxPositionalIndex, unnumberedCount)
    }
}
