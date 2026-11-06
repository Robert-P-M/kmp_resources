package dev.robdoes.kmpresources.domain.model

/**
 * Represents the state of a resource within the system.
 *
 * This enumeration defines the possible statuses for a resource, indicating whether
 * the resource is in a valid state, unused, or missing necessary translations.
 *
 * - `OK`: The resource is valid and complete.
 * - `UNUSED`: The resource exists but is not being utilized in the system.
 * - `MISSING_TRANSLATION`: The resource lacks required translations for one or more locales.
 */
internal enum class ResourceState {
    OK,
    UNUSED,
    MISSING_TRANSLATION
}

/**
 * Represents the evaluation of a resource within the system, including its main state
 * and the states of its associated sub-resources.
 *
 * This class is used to assess the condition of a resource holistically, identifying its
 * overall status (`mainState`) as well as the states of specific sub-resources (`subStates`).
 *
 * @property mainState The overall state of the resource, represented as a `ResourceState`.
 * @property subStates A map of sub-resources identified by their keys, where each sub-resource's
 * state is represented as a `ResourceState` or null if the state is undefined.
 */
internal data class ResourceEvaluation(
    val mainState: ResourceState,
    val subStates: Map<String, ResourceState?>
)