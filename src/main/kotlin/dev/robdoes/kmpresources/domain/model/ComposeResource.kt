package dev.robdoes.kmpresources.domain.model

sealed interface ComposeResource {
    val key: String
}

sealed interface XmlResource : ComposeResource {
    val type: ResourceType
    val isUntranslatable: Boolean
    val localizedValues: Map<String?, Any>
    val xmlTag: String get() = type.xmlTag
}

data class StringResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val values: Map<String?, String>
) : XmlResource {
    override val type = ResourceType.String
    override val localizedValues: Map<String?, Any> = values

    val defaultValue: String get() = values[null] ?: ""
}

data class PluralsResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, Map<String, String>>
) : XmlResource {
    override val type = ResourceType.Plural
    override val localizedValues: Map<String?, Any> = localizedItems
}

data class StringArrayResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, List<String>>
) : XmlResource {
    override val type = ResourceType.Array
    override val localizedValues: Map<String?, Any> = localizedItems
}