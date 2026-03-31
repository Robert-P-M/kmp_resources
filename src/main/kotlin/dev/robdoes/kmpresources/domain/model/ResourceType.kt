package dev.robdoes.kmpresources.domain.model

import dev.robdoes.kmpresources.domain.model.ResourceType.Companion.RES_PREFIX
import dev.robdoes.kmpresources.domain.model.ResourceType.Companion.entries


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
        override val xmlTag: kotlin.String = "string"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}$xmlTag"
        override val supportedQuantities: List<kotlin.String> = emptyList()
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
        override val xmlTag: kotlin.String = "plurals"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}$xmlTag"
        override val supportedQuantities: List<kotlin.String> = listOf("zero", "one", "two", "few", "many", "other")
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
        override val xmlTag: kotlin.String = "string-array"
        override val kotlinAccessor: kotlin.String = "${RES_PREFIX}array"
        override val supportedQuantities: List<kotlin.String> = emptyList()
    }

    /**
     * Companion object for the ResourceType class, providing utility methods and predefined constants
     * related to resources.
     *
     * @property RES_PREFIX A constant prefix used for resources.
     * @property entries A list of all defined ResourceType entries.
     */
    companion object {
        const val RES_PREFIX = "Res."
        val entries: List<ResourceType> = listOf(String, Plural, Array)

        /**
         * Retrieves a `ResourceType` instance corresponding to the provided XML tag.
         *
         * This method searches the entries of the `ResourceType` enumeration to find the
         * one that matches the specified XML tag. If no matching entry is found, it
         * returns null.
         *
         * @param tag The XML tag to search for within the enumeration entries.
         * @return The `ResourceType` that corresponds to the provided XML tag, or null if
         * no match is found.
         */
        fun fromXmlTag(tag: kotlin.String): ResourceType? =
            entries.find { it.xmlTag == tag }

        /**
         * Finds a `ResourceType` instance based on the provided Kotlin accessor.
         *
         * This function searches through the list of `ResourceType` entries and attempts to
         * match the given `accessor` with the `kotlinAccessor` field of each entry. A match is
         * found if the `accessor` starts with the corresponding `kotlinAccessor` concatenated
         * with a period ("."), and the function returns the matching `ResourceType`. If no match
         * is found, it returns `null`.
         *
         * @param accessor The Kotlin accessor string to be matched against the `kotlinAccessor`
         * field of the `ResourceType` entries.
         * @return The matching `ResourceType` if a match is found; otherwise, `null`.
         */
        fun fromKotlinAccessor(accessor: kotlin.String): ResourceType? =
            entries.find { accessor.startsWith(it.kotlinAccessor + ".") }
    }
}
