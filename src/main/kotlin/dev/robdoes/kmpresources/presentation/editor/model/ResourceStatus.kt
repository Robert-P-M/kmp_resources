package dev.robdoes.kmpresources.presentation.editor.model

import javax.swing.Icon

/**
 * Represents the status metadata of a resource in the context of resource management.
 *
 * @property icon An optional graphical representation associated with the resource status.
 * @property tooltip An optional text description or hint providing additional information about the resource status.
 */
internal data class ResourceStatus(val icon: Icon?, val tooltip: String?)