package dev.robdoes.kmpresources.data.repository

import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.domain.model.*

/**
 * Object responsible for parsing XML resource files and extracting localized resources such as strings,
 * plurals, and string arrays.
 *
 * This parser processes the root XML tag and iterates through its child tags to identify and convert them
 * into appropriate resource types, such as `StringResource`, `PluralsResource`, and `StringArrayResource`.
 * It supports filtering resources based on their translatable attribute and grouping the data by resource type.
 *
 * The parser relies on specific XML tag attributes such as "name" for the resource key, and "translatable"
 * to indicate whether a resource is untranslatable. It also processes nested tags like `<item>` for plurals
 * and arrays to extract values specific to each resource type.
 */
internal object XmlResourceParser {

    /**
     * Parses the given XML file to extract a list of defined resources.
     * These resources can represent strings, plurals, or string arrays and include their respective data.
     *
     * @param psiFile The XML file to be parsed, represented as an instance of `XmlFile`.
     *                This file is expected to contain resource definitions in its root tag.
     * @return A list of `XmlResource` objects representing the extracted resources.
     *         The list will be empty if the root tag is null or no resources are found.
     */
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

    /**
     * Retrieves the decoded text content from the given XML tag.
     *
     * @param tag The XML tag from which the text content will be extracted. It is expected to contain a value
     * with text elements that are concatenated to produce the final decoded string.
     * @return The concatenated string of the text elements within the given XML tag. If no text elements are present,
     * an empty string is returned.
     */
    private fun getDecodedText(tag: XmlTag): String {
        val textElements = tag.value.textElements
        return if (textElements.isNotEmpty()) {
            textElements.joinToString("") { it.value }
        } else ""
    }
}