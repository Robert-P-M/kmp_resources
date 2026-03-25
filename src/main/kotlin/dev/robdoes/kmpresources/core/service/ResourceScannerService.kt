package dev.robdoes.kmpresources.core.service

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import dev.robdoes.kmpresources.core.index.KMP_RESOURCE_USAGE_INDEX_NAME
import org.jetbrains.kotlin.idea.base.util.allScope

@Service(Service.Level.PROJECT)
class ResourceScannerService(private val project: Project) {

    fun isResourceUsed(keyName: String): Boolean {
        if (keyName.isBlank()) return false

        val normalizedKey = keyName.replace(".", "_").replace("-", "_")

        return try {
            ReadAction.compute<Boolean, Throwable> {
                val index = FileBasedIndex.getInstance()

                var found = false
                index.processValues(
                    KMP_RESOURCE_USAGE_INDEX_NAME,
                    normalizedKey,
                    null,
                    { _, _ ->
                        found = true
                        false
                    },
                    project.allScope()
                )
                found
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            true
        }
    }
}