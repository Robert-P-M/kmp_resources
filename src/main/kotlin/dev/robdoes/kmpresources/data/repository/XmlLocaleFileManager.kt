package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile

object XmlLocaleFileManager {

    fun findRelatedLocaleFiles(project: Project, defaultFile: VirtualFile): Map<String, XmlFile> {
        val valuesDir = defaultFile.parent ?: return emptyMap()
        val composeResourcesDir = valuesDir.parent ?: return emptyMap()
        val psiManager = PsiManager.getInstance(project)

        return composeResourcesDir.children
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .mapNotNull { dir ->
                val localeTag = dir.name.substringAfter("values-")
                val xmlFile = dir.findChild(defaultFile.name) ?: return@mapNotNull null
                val psiFile = psiManager.findFile(xmlFile) as? XmlFile ?: return@mapNotNull null
                localeTag to psiFile
            }.toMap()
    }

    fun createLocaleFileInternal(defaultFile: VirtualFile, localeTag: String): VirtualFile? {
        return runWriteAction {
            val defaultDir = defaultFile.parent ?: return@runWriteAction null
            val composeResourcesDir = defaultDir.parent ?: return@runWriteAction null
            val targetDirName = "values-$localeTag"

            val targetDir = composeResourcesDir.findChild(targetDirName)
                ?: composeResourcesDir.createChildDirectory(null, targetDirName)

            val targetFile = targetDir.findChild(defaultFile.name)
                ?: targetDir.createChildData(null, defaultFile.name)

            if (targetFile.length == 0L) {
                val initialContent = "<resources>\n</resources>"
                targetFile.setBinaryContent(initialContent.toByteArray(Charsets.UTF_8))
            }
            targetFile.refresh(false, false)
            targetFile
        }
    }
}