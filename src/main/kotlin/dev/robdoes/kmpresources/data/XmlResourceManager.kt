package dev.robdoes.kmpresources.data

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.domain.model.*

class XmlResourceManager(private val project: Project, private val file: VirtualFile) {

    private val logger = Logger.getInstance(XmlResourceManager::class.java)

    fun loadResources(): List<XmlResource> {
        val resources = mutableListOf<XmlResource>()
        val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return emptyList()
        val rootTag = psiFile.rootTag ?: return emptyList()

        for (tag in rootTag.subTags) {
            val name = tag.getAttributeValue("name") ?: continue
            val isUntranslatable = tag.getAttributeValue("translatable") == "false"

            when (tag.name) {
                "string" -> {
                    resources.add(StringResource(name, isUntranslatable, tag.value.text))
                }

                "plurals" -> {
                    val items = mutableMapOf<String, String>()
                    tag.findSubTags("item").forEach { itemTag ->
                        val quantity = itemTag.getAttributeValue("quantity")
                        if (quantity != null) items[quantity] = itemTag.value.text
                    }
                    resources.add(PluralsResource(name, isUntranslatable, items))
                }

                "string-array" -> {
                    val items = tag.findSubTags("item").map { it.value.text }
                    resources.add(StringArrayResource(name, isUntranslatable, items))
                }
            }
        }
        return resources
    }

    fun saveResource(resource: XmlResource) {
        WriteCommandAction.runWriteCommandAction(project, "Save KMP Resource", "KmpResourceEditor", {
            try {
                val rootTag = getRootTag() ?: return@runWriteCommandAction
                val factory = XmlElementFactory.getInstance(project)

                val translatableAttr = if (resource.isUntranslatable) " translatable=\"false\"" else ""
                val newTagStr = StringBuilder("<${resource.xmlTag} name=\"${resource.key}\"$translatableAttr>")

                when (resource) {
                    is StringResource -> newTagStr.append("${resource.value}</${resource.xmlTag}>")
                    is PluralsResource -> {
                        newTagStr.append("\n")
                        resource.items.forEach { (q, v) -> newTagStr.append("    <item quantity=\"$q\">$v</item>\n") }
                        newTagStr.append("</${resource.xmlTag}>")
                    }

                    is StringArrayResource -> {
                        newTagStr.append("\n")
                        resource.items.forEach { v -> newTagStr.append("    <item>$v</item>\n") }
                        newTagStr.append("</${resource.xmlTag}>")
                    }
                }

                val newTag = factory.createTagFromText(newTagStr.toString())

                val existingTag =
                    rootTag.subTags.find { it.name == resource.xmlTag && it.getAttributeValue("name") == resource.key }

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

    fun deleteResource(key: String, xmlTag: String) {
        WriteCommandAction.runWriteCommandAction(project, "Delete KMP Resource", "KmpResourceEditor", {
            try {
                val rootTag = getRootTag() ?: return@runWriteCommandAction
                rootTag.subTags.find { it.name == xmlTag && it.getAttributeValue("name") == key }?.delete()
            } catch (e: Exception) {
                logger.error("Error deleting resource $key", e)
            }
        })
    }

    fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Toggle Untranslatable", "KmpResourceEditor", {
            try {
                val tag =
                    getRootTag()?.subTags?.find { it.getAttributeValue("name") == key } ?: return@runWriteCommandAction
                if (isUntranslatable) tag.setAttribute("translatable", "false") else tag.getAttribute("translatable")
                    ?.delete()
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