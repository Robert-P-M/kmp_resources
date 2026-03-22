package dev.robdoes.kmpresources.domain.model

sealed interface ComposeResource {
    val key: String
}

sealed interface XmlResource : ComposeResource {
    val xmlTag: String
    val isUntranslatable: Boolean
}

data class StringResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val value: String
) : XmlResource {
    override val xmlTag = "string"
}

data class PluralsResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val items: Map<String, String>
) : XmlResource {
    override val xmlTag = "plurals"
}

data class StringArrayResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val items: List<String>
) : XmlResource {
    override val xmlTag = "string-array"
}