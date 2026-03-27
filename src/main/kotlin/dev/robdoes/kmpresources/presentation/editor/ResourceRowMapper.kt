package dev.robdoes.kmpresources.presentation.editor.ui

import com.intellij.icons.AllIcons
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.presentation.editor.model.ResourceColumn
import dev.robdoes.kmpresources.presentation.editor.model.ResourceStatus

object ResourceRowMapper {

    fun mapToRows(
        res: XmlResource,
        locales: List<LocaleInfo>,
        loadingStatus: ResourceStatus
    ): List<Array<Any?>> {
        val rows = mutableListOf<Array<Any?>>()

        when (res) {
            is StringResource -> {
                rows.add(createBaseRow(res, res.key, res.type.xmlTag, res.isUntranslatable, locales, loadingStatus))
            }

            is PluralsResource -> {
                rows.add(createBaseRow(res, res.key, res.type.xmlTag, res.isUntranslatable, locales, loadingStatus))
                res.type.supportedQuantities.forEach { q ->
                    val subRow = createBaseRow(res, "", q, false, locales, loadingStatus, quantity = q)
                    subRow[ResourceColumn.USAGE.index] = null
                    rows.add(subRow)
                }
            }

            is StringArrayResource -> {
                rows.add(createBaseRow(res, res.key, res.type.xmlTag, res.isUntranslatable, locales, loadingStatus))

                val defaultSize = res.localizedItems[null]?.size ?: 0
                val maxItems = locales.maxOfOrNull { res.localizedItems[it.languageTag]?.size ?: 0 } ?: 0
                val finalMax = maxOf(maxItems, defaultSize)

                for (i in 0 until finalMax) {
                    val subRow = createBaseRow(res, "", "item[$i]", false, locales, loadingStatus, index = i)
                    subRow[ResourceColumn.USAGE.index] = null
                    rows.add(subRow)
                }
                rows.add(createBaseRow(res, "", "item[+]", false, locales, loadingStatus).apply {
                    this[ResourceColumn.DELETE.index] = null
                })
            }
        }
        return rows
    }

    private fun createBaseRow(
        res: XmlResource,
        key: String,
        type: String,
        isUn: Boolean,
        locales: List<LocaleInfo>,
        status: ResourceStatus,
        quantity: String? = null,
        index: Int? = null
    ): Array<Any?> {
        val row = arrayOfNulls<Any?>(ResourceColumn.entries.size + locales.size)
        row[ResourceColumn.STATUS.index] = status
        row[ResourceColumn.KEY.index] = key
        row[ResourceColumn.USAGE.index] = if (key.isNotEmpty()) AllIcons.Actions.Search else null
        row[ResourceColumn.DELETE.index] = AllIcons.General.Remove
        row[ResourceColumn.UNTRANSLATABLE.index] = isUn
        row[ResourceColumn.TYPE.index] = type

        row[ResourceColumn.DEFAULT_VALUE.index] = getValue(res, null, quantity, index)

        locales.forEachIndexed { i, locale ->
            val colIdx = ResourceColumn.entries.size + i
            row[colIdx] = getValue(res, locale.languageTag, quantity, index)
        }

        return row
    }

    private fun getValue(resource: XmlResource, localeTag: String?, quantity: String?, index: Int?): String {
        return when (resource) {
            is StringResource -> resource.values[localeTag] ?: ""
            is PluralsResource -> resource.localizedItems[localeTag]?.get(quantity ?: "") ?: ""
            is StringArrayResource -> {
                val items = resource.localizedItems[localeTag] ?: emptyList()
                if (index != null && index in items.indices) items[index] else ""
            }
        }
    }
}