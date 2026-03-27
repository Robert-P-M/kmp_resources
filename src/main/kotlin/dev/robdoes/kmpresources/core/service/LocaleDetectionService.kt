package dev.robdoes.kmpresources.core.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.util.LocaleInfo
import dev.robdoes.kmpresources.core.util.LocaleProvider

@Service(Service.Level.PROJECT)
class LocaleDetectionService(private val project: Project) {

    suspend fun getActiveLocales(): List<LocaleInfo> {
        return readAction {
            val allLocales = mutableSetOf<String>()

            val root = project.guessProjectDir() ?: return@readAction emptyList()

            VfsUtil.processFilesRecursively(root) { file ->
                if (file.isDirectory &&
                    file.name.startsWith("values-") &&
                    file.path.contains("composeResources")
                ) {
                    val localeTag = file.name.removePrefix("values-")
                        .replace("-r", "-")
                    allLocales.add(localeTag)
                }
                true
            }

            LocaleProvider.getAvailableLocales()
                .filter { allLocales.contains(it.languageTag) }
                .sortedBy { it.displayName }
        }
    }

    suspend fun getDefaultLocale(): LocaleInfo {
        return LocaleInfo("default", KmpResourcesBundle.message("doc.popup.locale.default"), "")
    }
}