package dev.robdoes.kmpresources.core.shared

/**
 * Utility object for mapping BCP 47 language tags to corresponding directory names.
 *
 * This object provides a method to transform IETF BCP 47 language tags into folder names
 * that align with Android resource directory naming conventions. The mapping handles
 * different variations of language tags and formats them accordingly.
 *
 * Mapping behavior:
 * - If the input does not include a region identifier (e.g., "en"), it is mapped to the format `values-<language>`.
 * - If the input includes a region identifier with two characters (e.g., "en-US"), it is mapped to the format `values-<language>-r<REGION>`.
 * - Other formats are mapped using the prefix `values-b+`, with hyphens replaced by plus signs (e.g., "en-001" becomes `values-b+en+001`).
 */
object Bcp47FolderMapper {

    /**
     * Converts a BCP 47 language tag into a folder name suitable for Android resource directories.
     *
     * The method processes the input BCP 47 language tag and maps it to one of the following formats:
     * - `values-<language>` for tags without a region identifier (e.g., "en").
     * - `values-<language>-r<REGION>` for tags with a two-character region identifier (e.g., "en-US").
     * - `values-b+<language+region>` for other tags, replacing hyphens with plus signs (e.g., "en-001" becomes `values-b+en+001`).
     *
     * @param bcp47 The IETF BCP 47 language tag to be mapped (e.g., "en", "en-US", "es-419").
     * @return The corresponding folder name as a string (e.g., "values-en", "values-en-rUS", "values-b+es+419").
     */
    fun bcp47ToFolderName(bcp47: String): String {
        if (!bcp47.contains("-")) return "values-$bcp47"

        val parts = bcp47.split("-")
        
        if (parts.size == 2 && parts[1].length == 2) {
            return "values-${parts[0]}-r${parts[1].uppercase()}"
        }

        
        if (parts.size == 2 && parts[1].startsWith("r", ignoreCase = true) && parts[1].length == 3) {
            return "values-${parts[0]}-${parts[1]}"
        }
        
        return "values-b+${bcp47.replace("-", "+")}"
    }

    /**
     * Converts a folder name commonly used in resource directories to a BCP 47 language tag.
     *
     * The method handles folder names that adhere to a specific format, such as those used for
     * Android resource qualifiers, and converts them into valid BCP 47 language tags.
     * If the folder name matches the base value of the `valuesPrefix`, `null` is returned.
     * If the folder name does not follow the expected format, it is returned unchanged.
     *
     * @param folderName The name of the folder to be converted (e.g., "values-en-rUS").
     * @param valuesPrefix The prefix identifying the value folders (default is "values").
     * @return A formatted BCP 47 language tag if conversion is possible,
     *         the original folder name if it does not match the expected format,
     *         or `null` if the folder name is identical to the `valuesPrefix`.
     */
    fun folderNameToBcp47(folderName: String, valuesPrefix: String = "values"): String? {
        if (folderName == valuesPrefix) return null
        if (!folderName.startsWith("$valuesPrefix-")) return folderName

        val qualifier = folderName.substringAfter("$valuesPrefix-")

        if (qualifier.startsWith("b+")) {
            return qualifier.substringAfter("b+").replace("+", "-")
        }

        val parts = qualifier.split("-")
        if (parts.size == 2 && parts[1].startsWith("r") && parts[1].length == 3) {
            return "${parts[0]}-${parts[1].substring(1)}"
        }

        return qualifier
    }
}