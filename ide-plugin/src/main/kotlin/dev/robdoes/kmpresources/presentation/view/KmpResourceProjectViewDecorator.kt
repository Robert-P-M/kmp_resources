package dev.robdoes.kmpresources.presentation.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread-safe cache used to store the count of issues associated with specific file paths.
 *
 * The key is the file path as a [String], and the value is the corresponding issue count as an [Int].
 * This cache is utilized to optimize the retrieval of issue counts for resource files, avoiding redundant
 * computations and improving performance.
 *
 * The cache is typically updated with values obtained via asynchronous calculations and is cleared
 * when the project view needs to be invalidated.
 */
private val issueCache = ConcurrentHashMap<String, Int>()

/**
 * A thread-safe set used to track the file paths currently being processed for issue counting.
 *
 * This property ensures that the same file is not processed multiple times concurrently by maintaining
 * a set of file paths currently under evaluation. Each file path is added to this set before initiating
 * its processing and removed once the processing is complete.
 *
 * This helps in preventing redundant calculations and manages concurrency when analyzing files
 * within the project view decorator.
 */
private val isCalculating = ConcurrentHashMap.newKeySet<String>()

/**
 * Clears the cache used in the project view of the KMP Resources Tool Window.
 *
 * This method is responsible for invalidating and clearing the cached issue data,
 * ensuring that subsequent operations rely on up-to-date information. It is typically
 * called in response to events or actions that modify the resource files, ensuring
 * that the tool window reflects the latest state.
 *
 * Common use cases include:
 * - Transitioning out of dumb mode to refresh the project view.
 * - Responding to specific file events that indicate changes in the resource hierarchy.
 */
internal fun invalidateProjectViewCache() {
    issueCache.clear()
}

/**
 * A decorator for the Project View in IntelliJ IDEA that provides enhanced visualization for resource files
 * under specific conditions. This class is designed for use in a Kotlin Multiplatform (KMP) context and operates
 * as part of IntelliJ's plugin system.
 *
 * This decorator currently highlights XML resource files located under "values*" directories within the
 * "composeResources" directory. If issues (e.g., unused resources) are detected in such files, the corresponding
 * node in the Project View is annotated with a visual indicator representing the number of issues.
 *
 * The highlighting behavior ensures that:
 * - The file extension must be "xml".
 * - The parent directory name should start with "values".
 * - The file must reside in the "composeResources" directory.
 *
 * The issue detection process can be:
 * - Cached for subsequent views for performance optimization.
 * - Triggered asynchronously using IntelliJ's coroutine scope.
 *
 * This decorator manages the issue-detection lifecycle and properly integrates with IntelliJ's threading model.
 */
internal class KmpResourceProjectViewDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {

        val file = node.virtualFile ?: return
        if (!file.isValid) return
        val project = node.project ?: return
        val detectionService =
            project.service<dev.robdoes.kmpresources.core.application.service.ResourceSystemDetectionService>()
        val system = detectionService.detectSystem(file)

        if (file.extension == "xml" && file.parent?.name?.startsWith(system.valuesDirPrefix) == true && file.path.contains(
                system.baseResourceDirName
            )
        ) {
            val project = node.project ?: return
            val path = file.path

            val cachedCount = issueCache[path]

            if (cachedCount != null) {
                if (cachedCount > 0) {
                    val issueText = " [$cachedCount Issues]"
                    data.addText(issueText, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.ORANGE))
                }
            } else {
                if (isCalculating.add(path)) {
                    project.service<KmpProjectScopeService>().coroutineScope.launch {
                        try {
                            val issueService = project.service<ResourceIssueService>()
                            val count = issueService.countIssues(file)

                            issueCache[path] = count

                            withContext(Dispatchers.EDT) {
                                ProjectView.getInstance(project).refresh()
                            }
                        } finally {
                            isCalculating.remove(path)
                        }
                    }
                }
            }
        }
    }
}