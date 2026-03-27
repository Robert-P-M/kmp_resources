package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
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
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class XmlResourceRepositoryImpl(
    private val project: Project,
    private val file: VirtualFile
) : ResourceRepository {

    private val logger = Logger.getInstance(XmlResourceRepositoryImpl::class.java)

    override fun loadResources(): List<XmlResource> {
        return runReadAction {
            val defaultPsiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@runReadAction emptyList()
            val defaultResources = XmlResourceParser.parse(defaultPsiFile)
            val localeFiles = XmlLocaleFileManager.findRelatedLocaleFiles(project, file)

            val resourceMap = defaultResources.associateBy { it.key }.toMutableMap()

            localeFiles.forEach { (localeTag, psiFile) ->
                val localizedRes = XmlResourceParser.parse(psiFile)
                localizedRes.forEach { res ->
                    val existing = resourceMap[res.key]
                    if (existing != null) {
                        resourceMap[res.key] = mergeResource(existing, res, localeTag)
                    }
                }
            }
            resourceMap.values.toList()
        }
    }

    override suspend fun saveResource(resource: XmlResource) {
        val localesInResource = resource.localizedValues.keys

        localesInResource.forEach { localeTag ->
            if (localeTag == null) {
                saveToSpecificFile(file, resource, null)
            } else {
                val related = readAction { XmlLocaleFileManager.findRelatedLocaleFiles(project, file) }
                var targetFile = related[localeTag]?.virtualFile

                if (targetFile == null && !resource.isEmptyForLocale(localeTag)) {
                    targetFile = XmlLocaleFileManager.createLocaleFileInternal(file, localeTag)
                }

                targetFile?.let {
                    saveToSpecificFile(it, resource, localeTag)
                }
            }
        }
    }

    private fun saveToSpecificFile(targetFile: VirtualFile, resource: XmlResource, localeTag: String?) {
        WriteCommandAction.runWriteCommandAction(project, "Save KMP Resource ($localeTag)", "KmpResourceEditor", {
            try {
                val psiFile = PsiManager.getInstance(project).findFile(targetFile) as? XmlFile ?: return@runWriteCommandAction
                val rootTag = psiFile.rootTag ?: return@runWriteCommandAction

                val factory = XmlElementFactory.getInstance(project)
                val newTag = XmlResourceWriter.createResourceTag(factory, resource, localeTag)

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

                CodeStyleManager.getInstance(project).reformat(psiFile)
            } catch (e: Exception) {
                logger.error("Error saving to $localeTag", e)
            }
        })
    }

    override fun deleteResource(key: String, type: ResourceType) {
        val filesToDeleteFrom = mutableListOf(file)
        filesToDeleteFrom.addAll(XmlLocaleFileManager.findRelatedLocaleFiles(project, file).values.map { it.virtualFile })

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
                val tag = getRootTag()?.subTags?.find { it.getAttributeValue("name") == key } ?: return@runWriteCommandAction
                if (isUntranslatable) tag.setAttribute("translatable", "false")
                else tag.getAttribute("translatable")?.delete()
                CodeStyleManager.getInstance(project).reformat(tag)
            } catch (e: Exception) {
                logger.error("Error toggling translatable for $key", e)
            }
        })

        if (isUntranslatable) {
            val relatedFiles = XmlLocaleFileManager.findRelatedLocaleFiles(project, file)
            relatedFiles.forEach { (_, psiFile) ->
                WriteCommandAction.runWriteCommandAction(project, "Clean Translations", "KmpResourceEditor", {
                    psiFile.rootTag?.subTags?.find { it.getAttributeValue("name") == key }?.delete()
                })
            }
        }
    }

    private fun mergeResource(existing: XmlResource, incoming: XmlResource, localeTag: String): XmlResource {
        return when (existing) {
            is StringResource -> existing.copy(values = existing.values + (localeTag to ((incoming as StringResource).values[null] ?: "")))
            is PluralsResource -> existing.copy(localizedItems = existing.localizedItems + (localeTag to ((incoming as PluralsResource).localizedItems[null] ?: emptyMap())))
            is StringArrayResource -> existing.copy(localizedItems = existing.localizedItems + (localeTag to ((incoming as StringArrayResource).localizedItems[null] ?: emptyList())))
        }
    }

    private fun getRootTag(): XmlTag? = (PsiManager.getInstance(project).findFile(file) as? XmlFile)?.rootTag
}