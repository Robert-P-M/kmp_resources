package dev.robdoes.kmpresources.data.repository

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
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
            val scope = GlobalSearchScope.projectScope(project)
            val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)
            val psiManager = PsiManager.getInstance(project)

            xmlFiles.filter { file ->
                !file.isDirectory &&
                        file.parent?.name == "values" &&
                        file.path.contains("composeResources")
            }.mapNotNull { vFile ->
                val xml = psiManager.findFile(vFile) as? XmlFile ?: return@mapNotNull null

                if (xml.rootTag?.name == "resources") {
                    LocaleContext(
                        defaultValuesDirPath = vFile.parent.path,
                        defaultStringsFilePath = vFile.path // Das ist z.B. .../errors.xml
                    )
                } else null
            }
        }
    }

    override suspend fun localeFileExists(context: LocaleContext, locale: Locale): Boolean {
        return readAction {
            val defaultDir =
                VfsUtil.findFileByIoFile(File(context.defaultValuesDirPath), true) ?: return@readAction false
            val targetDirName = buildLocaleDirName(locale)
            val targetDir = defaultDir.parent?.findChild(targetDirName) ?: return@readAction false

            val fileName = File(context.defaultStringsFilePath).name
            val stringsFile = targetDir.findChild(fileName)

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

            val fileName = File(context.defaultStringsFilePath).name
            val stringsFile = targetDir.findChild(fileName)
                ?: targetDir.createChildData(this, fileName)

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

        val fileName = File(context.defaultStringsFilePath).name
        val stringsFile = File(localeDir, fileName)

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