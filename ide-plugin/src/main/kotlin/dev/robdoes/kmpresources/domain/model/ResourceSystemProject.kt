package dev.robdoes.kmpresources.domain.model

/**
 * Defines the type of resource system used in the application.
 *
 * This enum is utilized to distinguish between different platforms or frameworks
 * for resource management and rendering. Each value represents a unique system
 * for handling resources and defines platform-specific configurations or behavior.
 *
 * Enum entries:
 * - `COMPOSE_MULTIPLATFORM`: Indicates the use of Jetpack Compose Multiplatform for resource management.
 * - `ANDROID_NATIVE`: Represents the native Android platform's resource management system.
 */
internal enum class ResourceSystemType {
    COMPOSE_MULTIPLATFORM,
    ANDROID_NATIVE
}

/**
 * An interface that represents the structure and behavior of a resource system project.
 *
 * This interface is used to define common properties and methods for different types of
 * resource systems, such as Compose Multiplatform or Android Native. It defines key
 * characteristics of the resource system and provides utility methods for accessing
 * resource-specific data in a Kotlin-friendly manner.
 *
 * @property type The type of the resource system (e.g., Compose Multiplatform, Android Native).
 * @property baseResourceDirName The name of the base directory where resource files are stored.
 * @property valuesDirPrefix The prefix used for resource directory names that contain various
 * localized or platform-specific value definitions.
 * @property kotlinReferenceClass The fully qualified name of the Kotlin class used for resource
 * references in generated or user-written Kotlin code.
 * @property defaultFileName The default file name for resource storage or definitions within
 * the resource system.
 */

internal interface ResourceSystemProject {
    val type: ResourceSystemType
    val baseResourceDirName: String
    val valuesDirPrefix: String
    val kotlinReferenceClass: String
    val defaultFileName: String

    /**
     * Constructs a Kotlin-specific accessor prefix for the given resource type.
     *
     * This method generates a string that can be used as a reference prefix when working
     * with the specified resource type in Kotlin code. The prefix includes the fully
     * qualified name of the `kotlinReferenceClass` followed by the resource type's
     * identifier. For `Array` resources, the identifier will be explicitly set to "array".
     *
     * @param resourceType The type of resource for which the accessor prefix is generated.
     * This value determines the appropriate type identifier to include in the prefix.
     * @return A string representing the accessor prefix for the specified resource type,
     * formatted as `<kotlinReferenceClass>.<resourceType>.`.
     */
    fun getAccessorPrefix(resourceType: ResourceType): String {
        val typeName = if (resourceType == ResourceType.Array) "array" else resourceType.xmlTag
        return "$kotlinReferenceClass.$typeName."
    }
}

/**
 * A data object that represents the implementation of a resource system for
 * Jetpack Compose Multiplatform.
 *
 * This object provides specific configurations and metadata required to manage
 * resources in a Compose Multiplatform project. It overrides properties from
 * the `ResourceSystemProject` interface to define behavior specific to this
 * resource system type.
 *
 * Key properties include:
 * - The resource system type (`COMPOSE_MULTIPLATFORM`).
 * - The name of the base directory for resources (`composeResources`).
 * - The prefix for directories containing value definitions (`values`).
 * - The reference class used for Kotlin resource access (`Res`).
 * - The default file name for resource definitions (`strings.xml`).
 */
internal data object ComposeMultiplatformSystem : ResourceSystemProject {
    override val type = ResourceSystemType.COMPOSE_MULTIPLATFORM
    override val baseResourceDirName = "composeResources"
    override val valuesDirPrefix = "values"
    override val kotlinReferenceClass = "Res"
    override val defaultFileName = "string.xml"
}

/**
 * Represents the Android native resource system configuration.
 *
 * This object implements the `ResourceSystemProject` interface and provides configuration
 * information specific to the Android native platform. It defines the structure and conventions
 * used for managing and accessing resources within an Android project.
 *
 * Properties:
 * - `type`: Specifies that this resource system is of type `ANDROID_NATIVE`.
 * - `baseResourceDirName`: The base directory name where Android native resources are stored, which is `res`.
 * - `valuesDirPrefix`: The prefix for directories containing various localized or platform-specific values, which is `values`.
 * - `kotlinReferenceClass`: The Kotlin class name used for referencing resources, which is `R`.
 * - `defaultFileName`: The default file name for resource definitions, which is `strings.xml`.
 */
internal data object AndroidNativeSystem : ResourceSystemProject {
    override val type = ResourceSystemType.ANDROID_NATIVE
    override val baseResourceDirName = "res"
    override val valuesDirPrefix = "values"
    override val kotlinReferenceClass = "R"
    override val defaultFileName = "strings.xml"
}