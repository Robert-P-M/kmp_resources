package dev.robdoes.kmpresources.domain.model

import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.data.repository.XmlResourceWriter

sealed interface ComposeResource {
    val key: String
}

sealed interface XmlResource : ComposeResource {
    val type: ResourceType
    val isUntranslatable: Boolean
    val localizedValues: Map<String?, Any>
    val xmlTag: String get() = type.xmlTag

    fun isEmptyForLocale(localeTag: String?): Boolean
    fun writeContentToTag(factory: XmlElementFactory, tag: XmlTag, localeTag: String?)
}

data class StringResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val values: Map<String?, String>
) : XmlResource {
    override val type = ResourceType.String
    override val localizedValues: Map<String?, Any> = values

    val defaultValue: String get() = values[null] ?: ""
    override fun isEmptyForLocale(localeTag: String?): Boolean = values[localeTag].isNullOrBlank()
    override fun writeContentToTag(factory: XmlElementFactory, tag: XmlTag, localeTag: String?) {
        XmlResourceWriter.appendEscapedText(factory, tag, values[localeTag] ?: "")
    }
}

data class PluralsResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, Map<String, String>>
) : XmlResource {
    override val type = ResourceType.Plural
    override val localizedValues: Map<String?, Any> = localizedItems
    override fun isEmptyForLocale(localeTag: String?): Boolean = localizedItems[localeTag].isNullOrEmpty()
    override fun writeContentToTag(factory: XmlElementFactory, tag: XmlTag, localeTag: String?) {
        val items = localizedItems[localeTag] ?: emptyMap()
        items.forEach { (qty, value) ->
            val itemTag = factory.createTagFromText("<item quantity=\"$qty\"/>")
            XmlResourceWriter.appendEscapedText(factory, itemTag, value)
            tag.add(itemTag)
        }
    }
}

data class StringArrayResource(
    override val key: String,
    override val isUntranslatable: Boolean,
    val localizedItems: Map<String?, List<String>>
) : XmlResource {
    override val type = ResourceType.Array
    override val localizedValues: Map<String?, Any> = localizedItems
    override fun isEmptyForLocale(localeTag: String?): Boolean = localizedItems[localeTag].isNullOrEmpty()
    override fun writeContentToTag(factory: XmlElementFactory, tag: XmlTag, localeTag: String?) {
        val items = localizedItems[localeTag] ?: emptyList()
        items.forEach { value ->
            val itemTag = factory.createTagFromText("<item/>")
            XmlResourceWriter.appendEscapedText(factory, itemTag, value)
            tag.add(itemTag)
        }
    }
}