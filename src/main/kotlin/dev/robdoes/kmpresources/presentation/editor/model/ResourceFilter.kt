package dev.robdoes.kmpresources.presentation.editor.model

import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

enum class ResourceFilter(val bundleKey: String) {
    ALL("action.table.filter.all"),
    STRINGS("action.table.filter.strings"),
    PLURALS("action.table.filter.plurals"),
    ARRAYS("action.table.filter.arrays");

    fun getDisplayText(): String = KmpResourcesBundle.message(bundleKey)
}