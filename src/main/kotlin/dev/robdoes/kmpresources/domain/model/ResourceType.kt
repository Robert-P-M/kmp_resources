package dev.robdoes.kmpresources.domain.model


/**
 * Represents a type of resource within the system.
 *
 * This sealed interface defines the structure for different types of resources,
 * along with their associated metadata and behavior. Each resource type is
 * characterized by its XML tag, Kotlin accessor prefix, and supported pluralization
 * quantities (if applicable).
 *
 * The three concrete resource types are `String`, `Plural`, and `Array`.
 *
 * @property xmlTag The XML tag used to identify this resource type in resource files.
 * @property kotlinAccessor The prefix used as a Kotlin accessor for this resource type.
 * @property supportedQuantities A list of supported pluralization keys for this resource type,
 * if applicable. For resource types that do not support pluralization, this list will be empty.
 */
internal sealed interface ResourceType {
    val xmlTag: kotlin.String
    val kotlinAccessor: kotlin.String
    val supportedQuantities: List<kotlin.String>

    /**
     * Represents a string resource within the resource management system.
     *
     * This object defines metadata for string resources, including their XML tag,
     * Kotlin accessor, and supported quantity specifications.
     *
     * - `xmlTag`: The XML tag used to identify string resources in XML definitions.
     * - `kotlinAccessor`: The corresponding accessor used in Kotlin code to interact with these resources.
     * - `supportedQuantities`: A list indicating the supported quantity specifications; empty for string resources.
     *
     * Implements the `ResourceType` interface to cooperate with other resource types in the system.
     */
    data object String : ResourceType {
        override val xmlTag = "string"
        override val kotlinAccessor = "string"
        override val supportedQuantities = emptyList<kotlin.String>()
    }

    /**
     * Represents the `plurals` resource type within the system.
     *
     * This object defines the `plurals` XML resource type, commonly used in Android
     * and other platforms to represent localized strings with multiple quantity-based variants.
     * Quantity-based resources allow developers to specify different text for different
     * grammatical numbers (e.g., "zero", "one", "other").
     *
     * Key features of this resource type include:
     * - The XML tag associated with the resource is `plurals`.
     * - A predefined list of supported quantities is available, including `zero`, `one`,
     *   `two`, `few`, `many`, and `other`.
     * - A Kotlin-specific accessor name is provided for referencing this resource type.
     *
     * This class is a data object and implements the `ResourceType` interface.
     */
    data object Plural : ResourceType {
        override val xmlTag = "plurals"
        override val kotlinAccessor = "plurals"
        override val supportedQuantities = listOf("zero", "one", "two", "few", "many", "other")
    }

    /**
     * Represents a specialized resource type for string arrays within the resource management
     * system. This type corresponds to arrays of strings, useful for scenarios where grouped
     * or indexed strings are necessary, such as options in a dropdown or list items.
     *
     * Properties of this type include specific metadata for XML serialization and access
     * through Kotlin extensions.
     *
     * This object is defined as a singleton to ensure a single definition for this resource type
     * across the system.
     *
     * Implements:
     * - [ResourceType]: This indicates its role in the resource hierarchy and its compatibility
     *   with other resource-related functionality.
     *
     * Key features:
     * - `xmlTag`: Defines the tag used for serialization or identification in XML files.
     * - `kotlinAccessor`: Specifies the naming convention for accessing string-array resources
     *   programmatically.
     * - `supportedQuantities`: Indicates the absence of quantity-based variations, as this type
     *   does not support quantity-specific localizations.
     */
    data object Array : ResourceType {
        override val xmlTag = "string-array"
        override val kotlinAccessor = "array"
        override val supportedQuantities = emptyList<kotlin.String>()
    }

    /**
     * Companion object for the `ResourceType` class, providing utility methods and properties
     * for working with resource types.
     *
     * This object includes functions to retrieve `ResourceType` instances based on specific
     * criteria, such as XML tags or Kotlin-specific accessors. It also maintains a list of
     * predefined `ResourceType` entries for use within the system.
     */
    companion object {

        /**
         * Retrieves a `ResourceType` instance from the given XML tag.
         *
         * This function searches through all available `ResourceType` entries to find
         * one whose `xmlTag` matches the specified XML tag. If no match is found, it
         * returns `null`.
         *
         * @param xmlTag The XML tag string for which the corresponding `ResourceType`
         *               needs to be found.
         * @return The matching `ResourceType` if found, otherwise `null`.
         */
        fun fromXmlTag(xmlTag: kotlin.String): ResourceType? {
            return entries.find { it.xmlTag == xmlTag }
        }

        /**
         * Finds the matching resource type based on the provided Kotlin accessor string and resource system.
         *
         * This method searches through a collection of resource types to identify the first one
         * whose accessor prefix, as defined by the given `ResourceSystemProject`, matches the start
         * of the provided `accessor` string. The prefix is determined using the `getAccessorPrefix`
         * method of the resource system for each resource type.
         *
         * @param accessor The Kotlin accessor string used to locate the resource type.
         * @param system The `ResourceSystemProject` instance that defines the resource system and its accessor prefixes.
         * @return The matching `ResourceType`, or `null` if no matching resource type is found.
         */
        fun fromKotlinAccessor(accessor: kotlin.String, system: ResourceSystemProject): ResourceType? {
            return entries.find { accessor.startsWith(system.getAccessorPrefix(it)) }
        }

        /**
         * Defines the entries associated with the `ResourceType`.
         *
         * This list contains the supported types of resources that can be processed.
         * The entries include:
         * - `String`: Represents a single string resource.
         * - `Plural`: Represents a plural resource, supporting multiple quantities.
         * - `Array`: Represents an array of string resources.
         */
        val entries = listOf(String, Plural, Array)
    }
}
