package dev.robdoes.kmpresources.domain.model

/**
 * Represents a resource that is used in Compose-based applications.
 *
 * This interface provides a common structure for defining compose-specific resources,
 * identified by a unique key. It can be implemented by various types of resources
 * to ensure uniformity in resource representation and handling.
 *
 * Properties:
 * @property key A unique identifier used to distinguish this resource. This ensures that
 * each resource within a system can be uniquely identified and retrieved when required.
 */
internal sealed interface ComposeResource {
    val key: String
}

/**
 * Represents a base interface for XML-based resources used within Compose-based applications.
 *
 * This interface provides a unified structure for defining resources that are backed by XML
 * configurations, such as strings, plurals, and string arrays. Implementations of this
 * interface define specific types of resources based on their XML representation.
 *
 * Properties:
 * @property type The type of the resource, represented as a [ResourceType]. This indicates
 * the specific XML tag associated with the resource (e.g., string, plurals, string-array).
 * @property isUntranslatable A flag indicating whether the resource is untranslatable, meaning
 * that its value should not vary based on localization settings.
 * @property localizedValues A map containing localized values for the resource. The keys
 * are locale tags (e.g., "en", "fr") or `null` for default values, and the values are the
 * corresponding resource data.
 * @property xmlTag The associated XML tag for the resource, derived from the [ResourceType].
 *
 * Functions:
 * @function isEmptyForLocale Determines whether the resource is empty for a given locale.
 * This is used to identify cases where a particular locale does not have any meaningful
 * value defined for this resource.
 * @param localeTag The locale tag to check (e.g., "en", "fr", or `null` for the default).
 * @return `true` if the resource is empty for the given locale, `false` otherwise.
 */
internal sealed interface XmlResource : ComposeResource {
    val type: ResourceType
    val isUntranslatable: Boolean
    val localizedValues: Map<String?, Any>
    val xmlTag: String get() = type.xmlTag

    fun isEmptyForLocale(localeTag: String?): Boolean
}

/**
 * Represents a string resource in an XML-based resource system.
 *
 * This class is a specific implementation of the [XmlResource] interface, designed to handle
 * resources that correspond to single strings. It includes localized values for different
 * locales and provides methods to interact with them.
 *
 * @property key The unique identifier for this string resource.
 * @property isUntranslatable Specifies whether this string resource is not subject to localization.
 * @property values A map containing the localized string values for each locale tag.
 *
 * Functions:
 * @see XmlResource.isEmptyForLocale Checks if the string resource is empty or blank for a given locale.
 *
 * Overrides:
 * - [type]: Specifies that this resource is of type [ResourceType.String].
 * - [localizedValues]: Provides the same map as the `values` property with a more generic type.
 */
internal data class StringResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val values: Map<String?, String>
) : XmlResource {
    override val type = ResourceType.String
    override val localizedValues: Map<String?, Any> = values

    override fun isEmptyForLocale(localeTag: String?): Boolean = values[localeTag].isNullOrBlank()
}

/**
 * A representation of a plural resource that defines localized values based on quantity categories.
 *
 * This class models a plural resource, which handles multiple localized strings determined
 * by quantity categories such as "one", "few", "many", etc. It implements the [XmlResource]
 * interface, making it compatible with the resource management system in the application.
 *
 * Primary Properties:
 * @property key The unique identifier for the resource.
 * @property isUntranslatable Indicates whether the resource is excluded from translation.
 * @property localizedItems A map holding localized quantity-based strings for various locales.
 * Each key represents a locale tag (e.g., "en", "fr") or `null` for default values,
 * and the value is another map linking quantity categories (e.g., "one", "few")
 * to their corresponding localized strings.
 *
 * Inherited Properties:
 * @property type The type of the resource, always set to [ResourceType.Plural].
 * @property localizedValues A generic representation of the localized items map.
 *
 * Functions:
 * @function isEmptyForLocale Determines if the resource is empty or effectively unused for a
 * given locale. A locale's resource is considered empty if it does not contain any localized
 * strings or if the available strings are all blank.
 * @param localeTag The locale tag to evaluate (e.g., "en", "fr", or `null` for default values).
 * @return `true` if the resource is empty for the specified locale; otherwise, `false`.
 */
internal data class PluralsResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, Map<String, String>>
) : XmlResource {
    override val type = ResourceType.Plural
    override val localizedValues: Map<String?, Any> = localizedItems
    override fun isEmptyForLocale(localeTag: String?): Boolean {
        val items = localizedItems[localeTag]
        return items.isNullOrEmpty() || items.values.all { it.isBlank() }
    }

}

/**
 * Represents a string array resource used within the context of XML-based resource management.
 *
 * This class encapsulates localized string array values that can vary based on locale settings.
 * Each string array resource is associated with a unique key and contains mappings of locales to
 * lists of localized strings. It also provides metadata for resource type and translatability.
 *
 * @property key The unique identifier for the resource.
 * @property isUntranslatable A flag indicating if the resource is not meant to be translated across locales.
 * @property localizedItems A map containing localized string arrays. Keys represent locale tags
 * (e.g., "en", "fr") or `null` for the default localization, and values are lists of localized strings.
 */
internal data class StringArrayResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, List<String>>
) : XmlResource {
    override val type = ResourceType.Array
    override val localizedValues: Map<String?, Any> = localizedItems
    override fun isEmptyForLocale(localeTag: String?): Boolean {
        val items = localizedItems[localeTag]
        return items.isNullOrEmpty() || items.all { it.isBlank() }
    }

}