package dev.robdoes.kmpresources.domain.model

/**
 * Finds a resource of the specified type [T] within the list of [XmlResource]s that matches the given key.
 *
 * @param key The key associated with the resource to find.
 * @return The resource of type [T] if a match is found, or `null` if no matching resource is found or if it cannot be cast to the specified type [T].
 */
internal inline fun <reified T : XmlResource> List<XmlResource>.findResource(key: String): T? {
    return this.find { it.key == key } as? T
}

/**
 * Merges the current `XmlResource` instance with another incoming `XmlResource` instance
 * for a specific locale. The merge operation combines the localized values of the current
 * resource with those of the incoming resource.
 *
 * @param incoming The `XmlResource` instance to merge with the current resource.
 *                 It must be of the same type as the current resource.
 * @param localeTag The locale tag (e.g., "en", "fr") for which the merge will be performed.
 *                  This specifies the localization context for combining values.
 * @return A new `XmlResource` instance that represents the result of merging the current
 *         resource with the incoming resource for the specified locale.
 */
internal fun XmlResource.mergeWith(incoming: XmlResource, localeTag: String): XmlResource {
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