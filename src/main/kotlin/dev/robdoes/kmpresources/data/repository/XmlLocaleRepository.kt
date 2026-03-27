package dev.robdoes.kmpresources.data.repository

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.domain.repository.LocaleRepository
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.AddLocaleUseCase.LocaleContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

class XmlLocaleRepository(
    private val project: Project
) : LocaleRepository {

    override suspend fun findAllDefaultLocaleContexts(): List<LocaleContext> {
        return readAction {
            val basePath = project.basePath ?: return@readAction emptyList<LocaleContext>()
            val root = LocalFileSystem.getInstance()
                .findFileByPath(basePath) ?: return@readAction emptyList<LocaleContext>()

            val result = mutableListOf<LocaleContext>()
            val psiManager = PsiManager.getInstance(project)
            val candidates = mutableListOf<VirtualFile>()

            VfsUtil.processFilesRecursively(root) { file ->
                if (!file.isDirectory &&
                    (file.name == "strings.xml" || file.name == "string.xml") &&
                    file.parent?.name == "values" &&
                    file.path.contains("composeResources")
                ) {
                    candidates.add(file)
                }
                true
            }

            candidates.forEach { vFile ->
                val xml = psiManager.findFile(vFile) as? XmlFile ?: return@forEach
                if (xml.rootTag?.name == "resources") {
                    val valuesDir = vFile.parent
                    result.add(
                        LocaleContext(
                            defaultValuesDirPath = valuesDir.path,
                            defaultStringsFilePath = vFile.path
                        )
                    )
                }
            }

            result.toList()
        }
    }

    override suspend fun localeFileExists(context: LocaleContext, locale: Locale): Boolean {
        return readAction {
            val defaultDir =
                VfsUtil.findFileByIoFile(File(context.defaultValuesDirPath), true) ?: return@readAction false
            val targetDirName = buildLocaleDirName(locale)
            val targetDir = defaultDir.parent?.findChild(targetDirName) ?: return@readAction false
            val stringsFile = targetDir.findChild("strings.xml") ?: targetDir.findChild("string.xml")
            stringsFile != null
        }
    }

    override suspend fun createLocaleStructure(context: LocaleContext, locale: Locale) {
        edtWriteAction {
            val defaultDir = VfsUtil.findFileByIoFile(
                File(context.defaultValuesDirPath),
                true
            ) ?: return@edtWriteAction

            val targetDirName = buildLocaleDirName(locale)
            val parent = defaultDir.parent ?: return@edtWriteAction

            val targetDir = parent.findChild(targetDirName)
                ?: parent.createChildDirectory(this, targetDirName)

            val stringsFile = targetDir.findChild("strings.xml")
                ?: targetDir.createChildData(this, "strings.xml")

            if (stringsFile.contentsToByteArray().isEmpty()) {
                val initialContent = """
                <resources>
                </resources>
            """.trimIndent()
                stringsFile.setBinaryContent(initialContent.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    override fun createLocaleRepository(
        context: LocaleContext,
        locale: Locale,
        factory: (LocaleContext) -> ResourceRepository
    ): ResourceRepository {
        return factory(contextForLocale(context, locale))
    }

    private fun contextForLocale(context: LocaleContext, locale: Locale): LocaleContext {
        val defaultDir = File(context.defaultValuesDirPath)
        val parent = defaultDir.parentFile
        val dirName = buildLocaleDirName(locale)
        val localeDir = File(parent, dirName)
        val stringsFile = File(localeDir, "strings.xml")
        return LocaleContext(
            defaultValuesDirPath = localeDir.path,
            defaultStringsFilePath = stringsFile.path
        )
    }

    private fun buildLocaleDirName(locale: Locale): String {
        val language = locale.language.lowercase()
        val country = locale.country.uppercase()
        return when {
            language.isBlank() -> "values"
            country.isBlank() -> "values-$language"
            else -> "values-${language}-r$country"
        }
    }
}
