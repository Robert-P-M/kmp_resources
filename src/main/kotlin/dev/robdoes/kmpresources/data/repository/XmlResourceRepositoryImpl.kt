package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlStringUtil
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import kotlinx.coroutines.launch

class XmlResourceRepositoryImpl(
    private val project: Project,
    private val file: VirtualFile
) : ResourceRepository {

    private val logger = Logger.getInstance(XmlResourceRepositoryImpl::class.java)

    private fun parseFile(psiFile: XmlFile): List<XmlResource> {
        val resources = mutableListOf<XmlResource>()
        val rootTag = psiFile.rootTag ?: return emptyList()

        fun getDecodedText(tag: XmlTag): String {
            val textElements = tag.value.textElements
            return if (textElements.isNotEmpty()) {
                textElements.joinToString("") { it.value }
            } else ""
        }

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

    private fun mergeResource(existing: XmlResource, incoming: XmlResource, localeTag: String): XmlResource {
        return when (existing) {
            is StringResource -> {
                val incomingValue = (incoming as? StringResource)?.values?.get(null) ?: ""
                existing.copy(values = existing.values + (localeTag to incomingValue))
            }

            is PluralsResource -> {
                val incomingItems = (incoming as? PluralsResource)?.localizedItems?.get(null) ?: emptyMap()
                existing.copy(localizedItems = existing.localizedItems + (localeTag to incomingItems))
            }

            is StringArrayResource -> {
                val incomingItems = (incoming as? StringArrayResource)?.localizedItems?.get(null) ?: emptyList()
                existing.copy(localizedItems = existing.localizedItems + (localeTag to incomingItems))
            }
        }
    }

    override fun loadResources(): List<XmlResource> {
        val defaultPsiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return emptyList()
        val defaultResources = parseFile(defaultPsiFile)

        val localeFiles = findRelatedLocaleFiles()

        val resourceMap = defaultResources.associateBy { it.key }.toMutableMap()

        localeFiles.forEach { (localeTag, psiFile) ->
            val localizedRes = parseFile(psiFile)
            localizedRes.forEach { res ->
                val existing = resourceMap[res.key]
                if (existing != null) {
                    resourceMap[res.key] = mergeResource(existing, res, localeTag)
                }
            }
        }

        return resourceMap.values.toList()
    }

    private fun findRelatedLocaleFiles(): Map<String, XmlFile> {
        val valuesDir = file.parent ?: return emptyMap()
        val composeResourcesDir = valuesDir.parent ?: return emptyMap()
        val psiManager = PsiManager.getInstance(project)

        return composeResourcesDir.children
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .mapNotNull { dir ->
                val localeTag = dir.name.substringAfter("values-")
                val xmlFile = dir.findChild(file.name) ?: return@mapNotNull null
                val psiFile = psiManager.findFile(xmlFile) as? XmlFile ?: return@mapNotNull null
                localeTag to psiFile
            }.toMap()
    }


    override fun saveResource(resource: XmlResource) {
        project.service<KmpProjectScopeService>().coroutineScope.launch {

            val localesInResource = when (resource) {
                is StringResource -> resource.values.keys
                is PluralsResource -> resource.localizedItems.keys
                is StringArrayResource -> resource.localizedItems.keys
            }

            localesInResource.forEach { localeTag ->
                if (localeTag == null) {
                    saveToSpecificFile(file, resource, null)
                } else {
                    val related = readAction { findRelatedLocaleFiles() }
                    var targetFile = related[localeTag]?.virtualFile

                    if (targetFile == null && !isResourceEmptyForLocale(resource, localeTag)) {
                        targetFile = createLocaleFileInternal(localeTag)
                    }

                    targetFile?.let {
                        saveToSpecificFile(it, resource, localeTag)
                    }
                }
            }
        }
    }

    private suspend fun createLocaleFileInternal(localeTag: String): VirtualFile? {
        return com.intellij.openapi.application.edtWriteAction {
            val defaultDir = file.parent ?: return@edtWriteAction null
            val composeResourcesDir = defaultDir.parent ?: return@edtWriteAction null
            val targetDirName = "values-$localeTag"

            val targetDir = composeResourcesDir.findChild(targetDirName)
                ?: composeResourcesDir.createChildDirectory(null, targetDirName)

            val targetFile = targetDir.findChild(file.name)
                ?: targetDir.createChildData(null, file.name)

            if (targetFile.length == 0L) {
                val initialContent = "<resources>\n</resources>"
                targetFile.setBinaryContent(initialContent.toByteArray(Charsets.UTF_8))
            }
            targetFile.refresh(false, false)
            targetFile
        }
    }

    private fun saveToSpecificFile(targetFile: VirtualFile, resource: XmlResource, localeTag: String?) {
        WriteCommandAction.runWriteCommandAction(project, "Save KMP Resource ($localeTag)", "KmpResourceEditor", {
            try {
                val psiFile =
                    PsiManager.getInstance(project).findFile(targetFile) as? XmlFile ?: return@runWriteCommandAction
                val rootTag = psiFile.rootTag ?: return@runWriteCommandAction
                val factory = XmlElementFactory.getInstance(project)

                val newTag = factory.createTagFromText("<${resource.xmlTag} name=\"${resource.key}\"/>")

                if (localeTag == null && resource.isUntranslatable) {
                    newTag.setAttribute("translatable", "false")
                }

                fun appendEscapedText(targetTag: XmlTag, rawText: String) {
                    if (rawText.isEmpty()) return
                    var escaped = XmlStringUtil.escapeString(rawText)
                    escaped = escaped.replace("'", "\\'")
                    val dummyTag = factory.createTagFromText("<dummy>$escaped</dummy>")
                    dummyTag.value.textElements.forEach { textNode -> targetTag.add(textNode) }
                }

                when (resource) {
                    is StringResource -> {
                        appendEscapedText(newTag, resource.values[localeTag] ?: "")
                    }

                    is PluralsResource -> {
                        val items = resource.localizedItems[localeTag] ?: emptyMap()
                        items.forEach { (qty, value) ->
                            val itemTag = factory.createTagFromText("<item quantity=\"$qty\"/>")
                            appendEscapedText(itemTag, value)
                            newTag.add(itemTag)
                        }
                    }

                    is StringArrayResource -> {
                        val items = resource.localizedItems[localeTag] ?: emptyList()
                        items.forEach { value ->
                            val itemTag = factory.createTagFromText("<item/>")
                            appendEscapedText(itemTag, value)
                            newTag.add(itemTag)
                        }
                    }
                }

                val existingTag = rootTag.subTags.find {
                    it.name == resource.xmlTag && it.getAttributeValue("name") == resource.key
                }

                if (existingTag != null) {
                    val isEmpty = isResourceEmptyForLocale(resource, localeTag)
                    if (isEmpty && localeTag != null) {
                        existingTag.delete()
                    } else {
                        existingTag.replace(newTag)
                    }
                } else {
                    if (!isResourceEmptyForLocale(resource, localeTag)) {
                        rootTag.add(newTag)
                    }
                }

                CodeStyleManager.getInstance(project).reformat(psiFile)
            } catch (e: Exception) {
                logger.error("Error saving to $localeTag", e)
            }
        })
    }

    private fun isResourceEmptyForLocale(resource: XmlResource, localeTag: String?): Boolean {
        return when (resource) {
            is StringResource -> resource.values[localeTag].isNullOrBlank()
            is PluralsResource -> resource.localizedItems[localeTag].isNullOrEmpty()
            is StringArrayResource -> resource.localizedItems[localeTag].isNullOrEmpty()
        }
    }

    override fun deleteResource(key: String, type: ResourceType) {
        val filesToDeleteFrom = mutableListOf(file)
        filesToDeleteFrom.addAll(findRelatedLocaleFiles().values.map { it.virtualFile })

        filesToDeleteFrom.forEach { f ->
            WriteCommandAction.runWriteCommandAction(project, "Delete KMP Resource", "KmpResourceEditor", {
                val psiFile = PsiManager.getInstance(project).findFile(f) as? XmlFile
                psiFile?.rootTag?.subTags?.find {
                    it.name == type.xmlTag && it.getAttributeValue("name") == key
                }?.delete()
            })
        }
    }

    override fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        WriteCommandAction.runWriteCommandAction(project, "Toggle Untranslatable", "KmpResourceEditor", {
            try {
                val tag = getRootTag()?.subTags?.find { it.getAttributeValue("name") == key }
                    ?: return@runWriteCommandAction

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

        if (isUntranslatable) {
            val relatedFiles = findRelatedLocaleFiles()
            relatedFiles.forEach { (tag, psiFile) ->
                WriteCommandAction.runWriteCommandAction(project, "Clean Translations", "KmpResourceEditor", {
                    psiFile.rootTag?.subTags?.find {
                        it.getAttributeValue("name") == key
                    }?.delete()
                })
            }
        }
    }

    private fun getRootTag(): XmlTag? {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null
        return psiFile.rootTag
    }
}
