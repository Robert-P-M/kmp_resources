package dev.robdoes.kmpresources.core.shared

/**
 * Provides functionality to normalize resource keys by replacing invalid characters with underscores.
 *
 * Resource keys in certain contexts, such as localization files, might need normalization
 * to comply with specific naming conventions or to avoid conflicts. This object ensures
 * the keys are consistent by converting specific characters into underscores.
 *
 * Normalization rules:
 * - Replaces dots ('.') with underscores ('_').
 * - Replaces hyphens ('-') with underscores ('_').
 *
 * This utility is intended for internal use within the module to ensure uniform key formatting.
 */
internal object ResourceKeyNormalizer {
    fun normalize(rawKey: String): String {
        return rawKey.replace(".", "_").replace("-", "_")
    }
}