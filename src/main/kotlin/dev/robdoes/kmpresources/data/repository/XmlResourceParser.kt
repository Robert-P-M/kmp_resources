package dev.robdoes.kmpresources.data.repository

import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.domain.model.*

object XmlResourceParser {

    fun parse(psiFile: XmlFile): List<XmlResource> {
        val resources = mutableListOf<XmlResource>()
        val rootTag = psiFile.rootTag ?: return emptyList()

        for (tag in rootTag.subTags) {
            val name = tag.getAttributeValue("name") ?: continue
            val isUntranslatable = tag.getAttributeValue("translatable") == "false"

            val type = ResourceType.fromXmlTag(tag.name) ?: continue

            when (type) {
                ResourceType.String -> {
                    resources.add(StringResource(name, isUntranslatable, mapOf(null to getDecodedText(tag))))
                }

                ResourceType.Plural -> {
                    val items = tag.findSubTags("item").associate {
                        (it.getAttributeValue("quantity") ?: "unknown") to getDecodedText(it)
                    }
                    resources.add(PluralsResource(name, isUntranslatable, mapOf(null to items)))
                }

                ResourceType.Array -> {
                    val items = tag.findSubTags("item").map { getDecodedText(it) }
                    resources.add(StringArrayResource(name, isUntranslatable, mapOf(null to items)))
                }
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