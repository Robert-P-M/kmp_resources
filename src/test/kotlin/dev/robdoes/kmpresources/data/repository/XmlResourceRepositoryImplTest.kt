package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.application.service.KmpResourceWorkspaceService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.infrastructure.utils.KmpActionRunner
import dev.robdoes.kmpresources.domain.model.*
import dev.robdoes.kmpresources.domain.repository.ResourceRepository

class XmlResourceRepositoryImpl(
    private val project: Project,
    private val file: VirtualFile
) : ResourceRepository {

    private val logger = Logger.getInstance(XmlResourceRepositoryImpl::class.java)

    override fun loadResources(): List<XmlResource> {
        return project.service<KmpResourceWorkspaceService>().getResourceStateFlow(file).value
    }

    override suspend fun parseResourcesFromDisk(): List<XmlResource> {
        return runReadAction {
            val defaultPsiFile =
                PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return@runReadAction emptyList()
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
        val targetFilesMap = mutableMapOf<String?, VirtualFile>()

        for (localeTag in localesInResource) {
            if (localeTag == null) {
                targetFilesMap[null] = file
            } else {
                val related = readAction { XmlLocaleFileManager.findRelatedLocaleFiles(project, file) }
                var targetFile = related[localeTag]?.virtualFile

                if (targetFile == null && !resource.isEmptyForLocale(localeTag)) {
                    // FIX: Sicheres Dispatching, verhindert Deadlocks
                    targetFile = withEdtContext {
                        XmlLocaleFileManager.createLocaleFileInternal(file, localeTag)
                    }
                }

                if (targetFile != null) {
                    targetFilesMap[localeTag] = targetFile
                }
            }
        }

        if (targetFilesMap.isEmpty()) return

        val psiFilesMap = KmpActionRunner.runRead {
            targetFilesMap.mapNotNull { (locale, vFile) ->
                val xmlFile = PsiManager.getInstance(project).findFile(vFile) as? XmlFile
                if (xmlFile != null) locale to xmlFile else null
            }.toMap()
        }

        // FIX: Sicheres Dispatching, verhindert Deadlocks
        withEdtContext {
            KmpActionRunner.runWriteCommand(project, "Save KMP Resource", psiFilesMap.values.toList()) {
                psiFilesMap.forEach { (localeTag, psiFile) ->
                    XmlResourceWriter.writeResource(project, psiFile, resource, localeTag)
                }
            }
        }
    }

    override suspend fun deleteResource(key: String, type: ResourceType) {
        val filesToDeleteFrom = mutableListOf(file)

        val localeFiles = readAction {
            XmlLocaleFileManager.findRelatedLocaleFiles(project, file).values.map { it.virtualFile }
        }
        filesToDeleteFrom.addAll(localeFiles)

        val psiFiles = KmpActionRunner.runRead {
            filesToDeleteFrom.mapNotNull { PsiManager.getInstance(project).findFile(it) as? XmlFile }
        }

        if (psiFiles.isEmpty()) return

        // FIX: Sicheres Dispatching, verhindert Deadlocks
        withEdtContext {
            KmpActionRunner.runWriteCommand(project, "Delete KMP Resource", psiFiles) {
                psiFiles.forEach { psiFile ->
                    XmlResourceWriter.deleteResource(psiFile, key, type)
                }
            }
        }
    }

    override suspend fun toggleUntranslatable(key: String, isUntranslatable: Boolean) {
        val relatedFiles = runReadAction { XmlLocaleFileManager.findRelatedLocaleFiles(project, file) }
        val psiFilesToModify = mutableListOf<XmlFile>()

        KmpActionRunner.runRead {
            val defaultXmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile
            if (defaultXmlFile != null) psiFilesToModify.add(defaultXmlFile)

            if (isUntranslatable) {
                psiFilesToModify.addAll(relatedFiles.values)
            }
        }

        if (psiFilesToModify.isEmpty()) return

        KmpActionRunner.runWriteCommand(project, "Toggle Untranslatable", psiFilesToModify) {
            val defaultXmlFile = psiFilesToModify.firstOrNull { it.virtualFile == file }
            if (defaultXmlFile != null) {
                XmlResourceWriter.setUntranslatable(defaultXmlFile, key, isUntranslatable)
            }

            if (isUntranslatable) {
                psiFilesToModify.filter { it.virtualFile != file }.forEach { psiFile ->
                    XmlResourceWriter.deleteResourceByKey(psiFile, key)
                }
            }
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()
    }

    private fun mergeResource(existing: XmlResource, incoming: XmlResource, localeTag: String): XmlResource {
        return when (existing) {
            is StringResource -> existing.copy(
                values = existing.values + (localeTag to ((incoming as StringResource).values[null] ?: ""))
            )

            is PluralsResource -> existing.copy(
                localizedItems = existing.localizedItems + (localeTag to ((incoming as PluralsResource).localizedItems[null]
                    ?: emptyMap()))
            )

            is StringArrayResource -> existing.copy(
                localizedItems = existing.localizedItems + (localeTag to ((incoming as StringArrayResource).localizedItems[null]
                    ?: emptyList()))
            )
        }
    }
}