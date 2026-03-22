package dev.robdoes.kmpresources.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.TextOccurenceProcessor
import com.intellij.psi.search.UsageSearchContext

class ResourceScannerService(private val project: Project) {

    fun isResourceUsed(keyName: String): Boolean {
        if (keyName.isBlank()) return false

        return try {
            ReadAction.nonBlocking<Boolean> {
                var found = false
                val processor = TextOccurenceProcessor { element, _ ->
                    val currentFile = element.containingFile?.virtualFile ?: return@TextOccurenceProcessor true
                    val path = currentFile.path.replace("\\", "/")

                    if (path.contains("/build/") || path.contains("/generated/") || path.contains("/.idea/")) return@TextOccurenceProcessor true
                    if (currentFile.extension == "xml" || path.endsWith(".cvr")) return@TextOccurenceProcessor true

                    found = true
                    return@TextOccurenceProcessor false
                }

                PsiSearchHelper.getInstance(project).processElementsWithWord(
                    processor,
                    GlobalSearchScope.projectScope(project),
                    keyName,
                    UsageSearchContext.ANY,
                    true
                )

                found
            }
                .inSmartMode(project)
                .executeSynchronously()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }
}