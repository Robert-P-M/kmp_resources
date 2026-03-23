package dev.robdoes.kmpresources.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.util.Processor

@Service(Service.Level.PROJECT)
class ResourceScannerService(private val project: Project) {

    fun isResourceUsed(keyName: String): Boolean {
        if (keyName.isBlank()) return false

        val normalizedKey = keyName.replace(".", "_").replace("-", "_")

        return try {
            ReadAction.compute<Boolean, Throwable> {
                var found = false

                PsiSearchHelper.getInstance(project).processAllFilesWithWord(
                    normalizedKey,
                    GlobalSearchScope.projectScope(project),
                    Processor { psiFile ->
                        val vFile = psiFile.virtualFile ?: return@Processor true
                        val path = vFile.path.replace("\\", "/")

                        if (!path.contains("/build/") && !path.contains("/generated/") && vFile.extension != "xml") {
                            found = true
                            false
                        } else {
                            true
                        }
                    },
                    true
                )
                found
            }
        } catch (e: Exception) {
            true
        }
    }
}