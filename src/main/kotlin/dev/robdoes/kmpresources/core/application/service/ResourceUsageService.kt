package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import dev.robdoes.kmpresources.core.infrastructure.index.KMP_RESOURCE_USAGE_INDEX_NAME
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import org.jetbrains.kotlin.idea.base.util.allScope

@Service(Service.Level.PROJECT)
class ResourceUsageService(private val project: Project) {

    suspend fun isResourceUsed(keyName: String): Boolean {
        if (keyName.isBlank()) return false

        val normalizedKey =
            ResourceKeyNormalizer.normalize(keyName)
        return try {
            readAction {
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