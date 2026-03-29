package dev.robdoes.kmpresources.domain.model

inline fun <reified T : XmlResource> List<XmlResource>.findResource(key: String): T? {
    return this.find { it.key == key } as? T
}

fun XmlResource.mergeWith(incoming: XmlResource, localeTag: String): XmlResource {
    return when (this) {
        is StringResource -> this.copy(
            values = this.values + (localeTag to ((incoming as StringResource).values[null] ?: ""))
        )

        is PluralsResource -> this.copy(
            localizedItems = this.localizedItems + (localeTag to ((incoming as PluralsResource).localizedItems[null]
                ?: emptyMap()))
        )

        is StringArrayResource -> this.copy(
            localizedItems = this.localizedItems + (localeTag to ((incoming as StringArrayResource).localizedItems[null]
                ?: emptyList()))
        )
    }
}