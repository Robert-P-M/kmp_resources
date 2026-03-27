package dev.robdoes.kmpresources.domain.model

sealed interface ComposeResource {
    val key: String
}

sealed interface XmlResource : ComposeResource {
    val xmlTag: String
    val isUntranslatable: Boolean
    val localizedValues: Map<String?, Any>
}

data class StringResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val values: Map<String?, String>
) : XmlResource {
    override val xmlTag = "string"
    override val localizedValues: Map<String?, Any> = values

    val defaultValue: String get() = values[null] ?: ""
}

data class PluralsResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, Map<String, String>>
) : XmlResource {
    override val xmlTag = "plurals"
    override val localizedValues: Map<String?, Any> = localizedItems
}

data class StringArrayResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, List<String>>
) : XmlResource {
    override val xmlTag = "string-array"
    override val localizedValues: Map<String?, Any> = localizedItems
}