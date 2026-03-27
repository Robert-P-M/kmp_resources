package dev.robdoes.kmpresources.core.application.service

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleInfo
import dev.robdoes.kmpresources.core.shared.LocaleProvider

@Service(Service.Level.PROJECT)
class LocaleDetectionService(private val project: Project) {

    suspend fun getActiveLocales(): List<LocaleInfo> {
        return readAction {
            val result = mutableSetOf<String>()

            val scope = GlobalSearchScope.projectScope(project)
            val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)

            for (file in xmlFiles) {
                if (file.name != "strings.xml" && file.name != "string.xml") continue

                if (!file.path.contains("composeResources")) continue

                val parentDir = file.parent ?: continue

                if (parentDir.name.startsWith("values-")) {
                    val localeTag = parentDir.name.substringAfter("values-")
                    result.add(localeTag)
                }
            }

            val availableLocales = LocaleProvider.getAvailableLocales()
            result.mapNotNull { tag ->
                availableLocales.find { it.languageTag == tag }
            }.sortedBy { it.displayName }
        }
    }

    suspend fun getDefaultLocale(): LocaleInfo {
        return LocaleInfo("default", KmpResourcesBundle.message("doc.popup.locale.default"), "")
    }
}