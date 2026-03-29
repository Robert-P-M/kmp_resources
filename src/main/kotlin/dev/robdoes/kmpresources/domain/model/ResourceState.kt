package dev.robdoes.kmpresources.domain.model

enum class ResourceState {
    OK,
    UNUSED,
    MISSING_TRANSLATION
}

data class ResourceEvaluation(
    val mainState: ResourceState,
    val subStates: Map<String, ResourceState?>
)