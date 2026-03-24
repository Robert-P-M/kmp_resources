package dev.robdoes.kmpresources.ide.linter

object FormatArgumentAnalyzer {

    private val FORMAT_REGEX = """(?<!%)%([1-9]\d*\$)?(?:s|d)""".toRegex()

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
