package dev.robdoes.kmpresources.presentation.editor.model

/**
 * Represents the columns available in a resource table. Each column has an associated index
 * and a key for retrieving localized titles.
 *
 * @property index The position of the column in the table.
 * @property titleKey The resource key used for the column's localized title.
 * @constructor Creates a column with a specified index and title key.
 */
internal enum class ResourceColumn(val index: Int, val titleKey: String) {
    STATUS(0, "ui.table.column.status"),
    KEY(1, "ui.table.column.key"),
    USAGE(2, "ui.table.column.usage"),
    DELETE(3, "ui.table.column.delete"),
    UNTRANSLATABLE(4, "ui.table.column.untranslatable"),
    TYPE(5, "ui.table.column.type"),
    DEFAULT_VALUE(6, "ui.table.column.default.value");

    companion object {
        fun fromIndex(index: Int): ResourceColumn? = entries.find { it.index == index }
    }
}