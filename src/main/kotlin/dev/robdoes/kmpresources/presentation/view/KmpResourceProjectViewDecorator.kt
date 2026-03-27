package dev.robdoes.kmpresources.presentation.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import dev.robdoes.kmpresources.core.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.service.ResourceIssueService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private val issueCache = ConcurrentHashMap<String, Int>()
private val isCalculating = ConcurrentHashMap.newKeySet<String>()

fun invalidateProjectViewCache() {
    issueCache.clear()
}

class KmpResourceProjectViewDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (!file.isValid) return

        if (file.name == "strings.xml" && file.path.contains("composeResources")) {
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