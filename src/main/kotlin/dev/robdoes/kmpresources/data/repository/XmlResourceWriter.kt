package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.project.Project
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlStringUtil
import dev.robdoes.kmpresources.domain.model.*

object XmlResourceWriter {

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

    fun deleteResource(psiFile: XmlFile, key: String, type: ResourceType) {
        val targetTag = psiFile.rootTag?.subTags?.firstOrNull {
            it.name == type.xmlTag && it.getAttributeValue("name") == key
        }
        targetTag?.delete()
    }

    fun deleteResourceByKey(psiFile: XmlFile, key: String) {
        val targetTag = psiFile.rootTag?.subTags?.find { it.getAttributeValue("name") == key }
        targetTag?.delete()
    }

    fun setUntranslatable(psiFile: XmlFile, key: String, isUntranslatable: Boolean) {
        val tag = psiFile.rootTag?.subTags?.find { it.getAttributeValue("name") == key } ?: return
        if (isUntranslatable) {
            tag.setAttribute("translatable", "false")
        } else {
            tag.getAttribute("translatable")?.delete()
        }
    }

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

    fun appendEscapedText(factory: XmlElementFactory, targetTag: XmlTag, rawText: String) {
        if (rawText.isEmpty()) return

        var escaped = XmlStringUtil.escapeString(rawText)
        escaped = escaped.replace("'", "\\'")

        val dummyTag = factory.createTagFromText("<dummy>$escaped</dummy>")
        dummyTag.value.textElements.forEach { textNode -> targetTag.add(textNode) }
    }
}