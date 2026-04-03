package dev.robdoes.kmpresources.data.repository

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.application.service.ResourceSystemDetectionService
import dev.robdoes.kmpresources.core.shared.Bcp47FolderMapper
import dev.robdoes.kmpresources.domain.model.AndroidNativeSystem
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase.LocaleContext
import java.nio.charset.StandardCharsets

internal class XmlLocaleRepository(
    private val project: Project
) : LocaleRepository {

    override suspend fun findAllDefaultLocaleContexts(): List<LocaleContext> {
        return readAction {
            val scope = GlobalSearchScope.projectScope(project)
            val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)
            val psiManager = PsiManager.getInstance(project)
            val detectionService = project.service<ResourceSystemDetectionService>()

            xmlFiles
                .asSequence()
                .filter { file ->
                    val system = detectionService.detectSystem(file)
                    if (file.isDirectory) return@filter false
                    if (file.parent?.name != system.valuesDirPrefix) return@filter false
                    if (!file.path.contains(system.baseResourceDirName)) return@filter false
                    when (system) {
                        AndroidNativeSystem -> file.name == "strings.xml"
                        else -> file.name == "strings.xml" || file.name == "string.xml"
                    }
                }
                .mapNotNull { vFile ->
                    val xml = psiManager.findFile(vFile) as? XmlFile ?: return@mapNotNull null
                    if (xml.rootTag?.name != "resources") return@mapNotNull null

                    LocaleContext(
                        defaultValuesDirPath = vFile.parent.url,
                        defaultStringsFilePath = vFile.url,
                    )
                }
                .toList()
        }
    }

    override suspend fun localeFileExists(context: LocaleContext, localeTag: String): Boolean {
        return readAction {

            val defaultDir = VirtualFileManager.getInstance().findFileByUrl(context.defaultValuesDirPath)
                ?: return@readAction false

            val targetDirName = Bcp47FolderMapper.bcp47ToFolderName(localeTag)
            val targetDir = defaultDir.parent?.findChild(targetDirName) ?: return@readAction false


            val fileName = context.defaultStringsFilePath.substringAfterLast("/")
            val stringsFile = targetDir.findChild(fileName)

            stringsFile != null
        }
    }

    override suspend fun createLocaleStructure(context: LocaleContext, localeTag: String) {
        edtWriteAction {
            val defaultDir = VirtualFileManager.getInstance().findFileByUrl(context.defaultValuesDirPath)
                ?: return@edtWriteAction

            val targetDirName = Bcp47FolderMapper.bcp47ToFolderName(localeTag)
            val parent = defaultDir.parent ?: return@edtWriteAction

            val targetDir = parent.findChild(targetDirName) ?: parent.createChildDirectory(this, targetDirName)

            val fileName = context.defaultStringsFilePath.substringAfterLast("/")
            val stringsFile = targetDir.findChild(fileName) ?: targetDir.createChildData(this, fileName)

            if (stringsFile.contentsToByteArray().isEmpty()) {
                val initialContent = """
                    <resources>
                    </resources>
                """.trimIndent()
                stringsFile.setBinaryContent(initialContent.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }
}