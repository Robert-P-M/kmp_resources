package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.FileBasedIndex
import dev.robdoes.kmpresources.core.infrastructure.index.KMP_RESOURCE_USAGE_INDEX_NAME
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import org.jetbrains.kotlin.idea.base.util.allScope

/**
 * Service for evaluating resource usage within a project.
 *
 * This service provides functionality to determine whether a specific resource key is actively used
 * in the project by analyzing indexed references. It utilizes normalized resource keys for accurate matching
 * and handles various exception scenarios gracefully.
 *
 * @property project The project context in which this service operates.
 */
@Service(Service.Level.PROJECT)
internal class ResourceUsageService(private val project: Project) {

    /**
     * Determines whether a specific resource, identified by its key, is actively used in the project.
     *
     * The method checks for the presence of the given resource key in the project's index.
     * The key is normalized before being used for lookup to enhance consistency and accuracy.
     * If the resource key is blank, it returns `false` immediately.
     * Handles cancellation and other exceptions that may occur during the index processing.
     *
     * @param keyName The key of the resource to check for usage. The provided key must be non-blank.
     * @return `true` if the resource is found to be used in the project, `false` otherwise.
     */
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