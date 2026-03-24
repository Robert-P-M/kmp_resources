package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlStringUtil
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class XmlResourceRepositoryImpl(
    private val project: Project,
    private val file: VirtualFile
) : ResourceRepository {

    private val logger = Logger.getInstance(XmlResourceRepositoryImpl::class.java)

    override fun loadResources(): List<XmlResource> {
        val resources = mutableListOf<XmlResource>()
        val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return emptyList()
        val rootTag = psiFile.rootTag ?: return emptyList()

        fun getDecodedText(tag: XmlTag): String {
            val textElements = tag.value.textElements
            if (textElements.isNotEmpty()) {
                return textElements.joinToString("") { it.value }
            }
            return ""
        }

        for (tag in rootTag.subTags) {
            val name = tag.getAttributeValue("name") ?: continue
            val isUntranslatable = tag.getAttributeValue("translatable") == "false"

            when (tag.name) {
                "string" -> resources.add(StringResource(name, isUntranslatable, getDecodedText(tag)))
                "plurals" -> {
                    val items = mutableMapOf<String, String>()
                    tag.findSubTags("item").forEach { itemTag ->
                        val quantity = itemTag.getAttributeValue("quantity")
                        if (quantity != null) items[quantity] = getDecodedText(itemTag)
                    }
                    resources.add(PluralsResource(name, isUntranslatable, items))
                }

                "string-array" -> {
                    val items = tag.findSubTags("item").map { getDecodedText(it) }
                    resources.add(StringArrayResource(name, isUntranslatable, items))
                }
            }
        }
        return resources
    }

    override fun saveResource(resource: XmlResource) {
        WriteCommandAction.runWriteCommandAction(project, "Save KMP Resource", "KmpResourceEditor", {
            try {
                val rootTag = getRootTag() ?: return@runWriteCommandAction
                val factory = XmlElementFactory.getInstance(project)

                val tagBuilder = java.lang.StringBuilder()

                val translatableAttr = if (resource.isUntranslatable) " translatable=\"false\"" else ""
                tagBuilder.append("<${resource.xmlTag} name=\"${resource.key}\"$translatableAttr>")

                when (resource) {
                    is StringResource -> {
                        tagBuilder.append(XmlStringUtil.escapeString(resource.value))
                        tagBuilder.append("</${resource.xmlTag}>")
                    }

                    is PluralsResource -> {
                        tagBuilder.append("\n")
                        resource.items.forEach { (quantity, value) ->
                            tagBuilder.append("    <item quantity=\"$quantity\">")
                            tagBuilder.append(XmlStringUtil.escapeString(value))
                            tagBuilder.append("</item>\n")
                        }
                        tagBuilder.append("</${resource.xmlTag}>")
                    }

                    is StringArrayResource -> {
                        tagBuilder.append("\n")
                        resource.items.forEach { value ->
                            tagBuilder.append("    <item>")
                            tagBuilder.append(XmlStringUtil.escapeString(value))
                            tagBuilder.append("</item>\n")
                        }
                        tagBuilder.append("</${resource.xmlTag}>")
                    }
                }

                val newTag = factory.createTagFromText(tagBuilder.toString())

                val existingTag = rootTag.subTags.find {
                    it.name == resource.xmlTag && it.getAttributeValue("name") == resource.key
                }

                if (existingTag != null) {
                    val replacedTag = existingTag.replace(newTag)
                    CodeStyleManager.getInstance(project).reformat(replacedTag)
                } else {
                    val addedTag = rootTag.add(newTag)
                    CodeStyleManager.getInstance(project).reformat(addedTag)
                }

            } catch (e: Exception) {
                logger.error("Error saving resource ${resource.key}", e)
            }
        })
    }

    override fun deleteResource(key: String, xmlTag: String) {
        WriteCommandAction.runWriteCommandAction(project, "Delete KMP Resource", "KmpResourceEditor", {
            try {
                val rootTag = getRootTag() ?: return@runWriteCommandAction
                rootTag.subTags.find { it.name == xmlTag && it.getAttributeValue("name") == key }?.delete()
            } catch (e: Exception) {
                logger.error("Error deleting resource $key", e)
            }
        })
    }

    override fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Toggle Untranslatable", "KmpResourceEditor", {
            try {
                val tag =
                    getRootTag()?.subTags?.find { it.getAttributeValue("name") == key } ?: return@runWriteCommandAction
                if (isUntranslatable) {
                    tag.setAttribute("translatable", "false")
                } else {
                    tag.getAttribute("translatable")?.delete()
                }
                CodeStyleManager.getInstance(project).reformat(tag)
            } catch (e: Exception) {
                logger.error("Error toggling translatable for $key", e)
            }
        })
    }

    private fun getRootTag(): XmlTag? {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        return psiFile.rootTag
    }
}
