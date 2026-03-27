package dev.robdoes.kmpresources.data.repository

import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlStringUtil
import dev.robdoes.kmpresources.domain.model.XmlResource

object XmlResourceWriter {

    fun createResourceTag(factory: XmlElementFactory, resource: XmlResource, localeTag: String?): XmlTag {
        val newTag = factory.createTagFromText("<${resource.xmlTag} name=\"${resource.key}\"/>")

        if (localeTag == null && resource.isUntranslatable) {
            newTag.setAttribute("translatable", "false")
        }

        resource.writeContentToTag(factory, newTag, localeTag)

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