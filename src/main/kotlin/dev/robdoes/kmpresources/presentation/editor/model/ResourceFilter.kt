package dev.robdoes.kmpresources.presentation.editor.model

import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

/**
 * Represents a filter type used for categorizing resources in the editor.
 *
 * @property bundleKey The key used to retrieve the corresponding localized display text
 * from the resource bundle.
 */
internal enum class ResourceFilter(val bundleKey: String) {
    ALL("action.table.filter.all"),
    STRINGS("action.table.filter.strings"),
    PLURALS("action.table.filter.plurals"),
    ARRAYS("action.table.filter.arrays");

    fun getDisplayText(): String = KmpResourcesBundle.message(bundleKey)
}