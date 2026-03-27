package dev.robdoes.kmpresources.domain.model

sealed interface ResourceType {
    val xmlTag: kotlin.String
    val kotlinAccessor: kotlin.String

    data object String : ResourceType {
        override val xmlTag: kotlin.String = "string"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}$xmlTag"
    }

    data object Plural : ResourceType {
        override val xmlTag: kotlin.String = "plurals"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}$xmlTag"
    }

    data object Array : ResourceType {
        override val xmlTag: kotlin.String = "string-array"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}$xmlTag"
    }

    companion object {
        const val RES_PREFIX = "Res."
        val entries: List<ResourceType> = listOf(String, Plural, Array)

        fun fromXmlTag(tag: kotlin.String): ResourceType? =
            entries.find { it.xmlTag == tag }

        fun fromKotlinAccessor(accessor: kotlin.String): ResourceType? =
            entries.find { accessor.startsWith(it.kotlinAccessor + ".") }
    }
}
