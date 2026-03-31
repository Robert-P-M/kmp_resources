package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.project.Project
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlStringUtil
import dev.robdoes.kmpresources.domain.model.*

/**
 * Utility object for managing XML resources in Android projects. This object provides functions to
 * write, delete, and modify XML resource files programmatically.
 */
internal object XmlResourceWriter {

    /**
     * Writes a given XML resource to the specified XML file. If the resource already exists in the file,
     * it will be replaced or deleted based on the locale and resource content. If it does not exist,
     * the resource will be added.
     *
     * @param project The IntelliJ IDEA project where the XML file is being modified.
     * @*/
    fun writeResource(project: Project, psiFile: XmlFile, resource: XmlResource, localeTag: String?) {
        val rootTag = psiFile.rootTag ?: return
        val factory = XmlElementFactory.getInstance(project)

        val newTag = createResourceTag(factory, resource, localeTag)
        val existingTag = rootTag.subTags.find {
            it.name == resource.xmlTag && it.getAttributeValue("name") == resource.key
        }

        if (existingTag != null) {
            if (resource.isEmptyForLocale(localeTag) && localeTag != null) {
                existingTag.delete()
            } else {
                existingTag.replace(newTag)
            }
        } else if (!resource.isEmptyForLocale(localeTag)) {
            rootTag.add(newTag)
        }

    }

    /**
     * Deletes a specific resource from the given XML file based on the provided key and resource type.
     *
     * This function identifies the target XML tag within the root tag of the specified XML file
     * that matches the given resource type and key. If the tag is found, it is removed from the
     * XML file.
     *
     * @param psiFile The XML file containing the resources. It is represented as an instance of `XmlFile`.
     *                The file should have a root tag with child tags representing various resources.
     * @param key The unique identifier (name attribute) of the resource to be deleted.
     * @param type The type of the resource to delete, specified as a `ResourceType`. It determines
     *             the XML tag name associated with the resource (e.g., "string", "plurals", "string-array").
     */
    fun deleteResource(psiFile: XmlFile, key: String, type: ResourceType) {
        val targetTag = psiFile.rootTag?.subTags?.firstOrNull {
            it.name == type.xmlTag && it.getAttributeValue("name") == key
        }
        targetTag?.delete()
    }

    /**
     * Deletes a resource defined by the specified key from the provided XML file.
     *
     * @param psiFile The XML file from which the resource will be deleted. This file is represented as an instance of `XmlFile` and is expected to have resource definitions within
     *  its root tag.
     * @param key The key of the resource to be deleted. This key corresponds to the value of the "name" attribute in an XML tag.
     */
    fun deleteResourceByKey(psiFile: XmlFile, key: String) {
        val targetTag = psiFile.rootTag?.subTags?.find { it.getAttributeValue("name") == key }
        targetTag?.delete()
    }

    /**
     * Updates the "translatable" attribute of a specified tag in the provided XML file.
     *
     * @param psiFile The XML file where the tag is located. Represented as an instance of `XmlFile`.
     *                The file is expected to contain a root tag with child tags.
     * @param key The value of the "name" attribute of the tag to be updated. If no matching tag is found, no action is performed.
     * @param isUntranslatable A Boolean value indicating whether the "translatable" attribute should
     *                         be set to "false" (`true` for untranslatable) or removed if present (`false` for translatable).
     */
    fun setUntranslatable(psiFile: XmlFile, key: String, isUntranslatable: Boolean) {
        val tag = psiFile.rootTag?.subTags?.find { it.getAttributeValue("name") == key } ?: return
        if (isUntranslatable) {
            tag.setAttribute("translatable", "false")
        } else {
            tag.getAttribute("translatable")?.delete()
        }
    }

    /**
     * Creates an XML tag for a given resource with appropriate attributes and nested child elements.
     *
     * The method handles different types of resources: `StringResource`, `PluralsResource`, and `StringArrayResource`,
     * generating their corresponding XML representation. If the resource is untranslatable and no specific locale
     * tag is provided, the method sets the `translatable` attribute to `false`.
     *
     * For `StringResource`, the value is added as text content to the tag.
     * For `PluralsResource`, child `<item>` tags are added for each quantity with their respective values.
     * For `StringArrayResource`, child `<item>` tags are added for each element in the array.
     *
     * @param tag Object responsible for creating and manipulating XML elements.
     * @param resource The resource object containing key, attributes, and localized values.
     * @param localeTag The locale identifier to determine which localized values to use. A null value indicates
     *                  the default or fallback locale.
     * @return The resulting `XmlTag` representing the resource with properly escaped and structured content.
     */
    fun createResourceTag(factory: XmlElementFactory, resource: XmlResource, localeTag: String?): XmlTag {
        val newTag = factory.createTagFromText("<${resource.xmlTag} name=\"${resource.key}\"/>")

        if (localeTag == null && resource.isUntranslatable) {
            newTag.setAttribute("translatable", "false")
        }
        when (resource) {
            is StringResource -> {
                val value = resource.values[localeTag] ?: ""
                appendEscapedText(factory, newTag, value)
            }

            is PluralsResource -> {
                val items = resource.localizedItems[localeTag] ?: emptyMap()
                items.forEach { (qty, value) ->
                    if (value.isNotBlank()) {
                        val itemTag = factory.createTagFromText("<item quantity=\"$qty\"/>")
                        appendEscapedText(factory, itemTag, value)
                        newTag.add(itemTag)
                    }
                }
            }

            is StringArrayResource -> {
                val items = resource.localizedItems[localeTag] ?: emptyList()
                items.forEach { value ->
                    if (value.isNotBlank()) {
                        val itemTag = factory.createTagFromText("<item/>")
                        appendEscapedText(factory, itemTag, value)
                        newTag.add(itemTag)
                    }
                }
            }
        }

        return newTag
    }

    /**
     * Appends text to the target XML tag after escaping it for safe XML representation.
     * This method ensures that the provided raw text is properly escaped and added as
     * separate text nodes to the specified XML tag.
     *
     * @param factory The `XmlElementFactory` instance used to create intermediate XML elements.
     * @param targetTag The `XmlTag` to which the escaped text will be appended.
     * @param rawText The raw string to be escaped and added to the target XML tag.
     *                If the string is empty, the method exits without performing any operation.
     */
    fun appendEscapedText(factory: XmlElementFactory, targetTag: XmlTag, rawText: String) {
        if (rawText.isEmpty()) return

        var escaped = XmlStringUtil.escapeString(rawText)
        escaped = escaped.replace("'", "\\'")

        val dummyTag = factory.createTagFromText("<dummy>$escaped</dummy>")
        dummyTag.value.textElements.forEach { textNode -> targetTag.add(textNode) }
    }
}