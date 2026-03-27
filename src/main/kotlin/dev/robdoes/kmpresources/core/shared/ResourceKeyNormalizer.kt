package dev.robdoes.kmpresources.core.shared

object ResourceKeyNormalizer {
    fun normalize(rawKey: String): String {
        return rawKey.replace(".", "_").replace("-", "_")
    }
}