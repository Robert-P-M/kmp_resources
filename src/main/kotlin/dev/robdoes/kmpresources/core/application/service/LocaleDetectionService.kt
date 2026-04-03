package dev.robdoes.kmpresources.core.application.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.Bcp47FolderMapper
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.core.shared.LocaleProvider
import dev.robdoes.kmpresources.domain.usecase.LocaleFormatValidator

/**
 * Service for detecting and managing locales within a project.
 *
 * This service provides functionality to identify all active locales
 * used in the project and to obtain the default locale. It scans the
 * project's resource files to determine locale-specific resource configurations.
 *
 * @constructor Initializes the service with the given project instance.
 */
@Service(Service.Level.PROJECT)
internal class LocaleDetectionService(private val project: Project) {

    /**
     * Retrieves a list of active locales within the project's resources.
     *
     * This method scans the project's resource directories for locale-specific
     * configurations (e.g., directories named `values-<localeTag>` containing valid
     * XML resource files). It matches the found locale tags with the list of
     * available locales and returns a list of matching `LocaleInfo` objects.
     *
     * @return A sorted list of `LocaleInfo` objects representing active locales in the project.
     */
    suspend fun getActiveLocales(): List<LocaleInfo> {
        return readAction {
            val result = mutableSetOf<String>()

            val scope = GlobalSearchScope.projectScope(project)
            val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)

            val detectionService = project.service<ResourceSystemDetectionService>()

            for (file in xmlFiles) {
                if (file.name != "strings.xml" && file.name != "string.xml") continue

                val system = detectionService.detectSystem(file)
                if (!file.path.contains(system.baseResourceDirName)) continue

                val parentDir = file.parent ?: continue

                if (parentDir.name.startsWith("${system.valuesDirPrefix}-")) {
                    val bcp47Tag = Bcp47FolderMapper.folderNameToBcp47(parentDir.name, system.valuesDirPrefix)
                    if (bcp47Tag == null || !LocaleFormatValidator.isValid(bcp47Tag)) continue
                    val xmlFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: continue

                    if (xmlFile.rootTag?.name == "resources") {
                        result.add(bcp47Tag)
                    }
                }
            }

            val availableLocales = LocaleProvider.getAvailableLocales()
            result.mapNotNull { tag ->
                availableLocales.find { it.languageTag == tag }
            }.sortedBy { it.displayName }
        }
    }

    /**
     * Retrieves the default locale for the project.
     *
     * This method returns a `LocaleInfo` object representing the default locale,
     * which serves as a fallback when no specific locale is selected.
     *
     * @return A `LocaleInfo` object containing information about the default locale,
     *         including its language tag, display name, and flag emoji (if applicable).
     */
    suspend fun getDefaultLocale(): LocaleInfo {
        return LocaleInfo("default", KmpResourcesBundle.message("doc.popup.locale.default"), "")
    }
}