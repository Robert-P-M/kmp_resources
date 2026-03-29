package dev.robdoes.kmpresources.data.repository

import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.domain.model.*

object XmlResourceParser {

    fun parse(psiFile: XmlFile): List<XmlResource> {
        val resources = mutableListOf<XmlResource>()
        val rootTag = psiFile.rootTag ?: return emptyList()

        for (tag in rootTag.subTags) {
            val key = tag.getAttributeValue("name") ?: continue
            if (key.isBlank()) continue

            val isUntranslatable = tag.getAttributeValue("translatable") == "false"

            when (ResourceType.fromXmlTag(tag.name)) {
                ResourceType.String -> {
                    val value = getDecodedText(tag)
                    resources.add(StringResource(key, isUntranslatable, mapOf(null to value)))
                }

                ResourceType.Plural -> {
                    val items = mutableMapOf<String, String>()
                    tag.findSubTags("item").forEach { itemTag ->
                        val quantity = itemTag.getAttributeValue("quantity") ?: return@forEach
                        val value = getDecodedText(itemTag)
                        items[quantity] = value
                    }
                    resources.add(PluralsResource(key, isUntranslatable, mapOf(null to items)))
                }

                ResourceType.Array -> {
                    val items = mutableListOf<String>()
                    tag.findSubTags("item").forEach { itemTag ->
                        val value = getDecodedText(itemTag)
                        items.add(value)
                    }
                    resources.add(StringArrayResource(key, isUntranslatable, mapOf(null to items)))
                }

                null -> continue
            }
        }
        return resources
    }

    private fun getDecodedText(tag: XmlTag): String {
        val textElements = tag.value.textElements
        return if (textElements.isNotEmpty()) {
            textElements.joinToString("") { it.value }
        } else ""
    }
}