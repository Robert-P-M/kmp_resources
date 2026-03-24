package dev.robdoes.kmpresources.presentation.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import dev.robdoes.kmpresources.core.service.ResourceIssueService

class KmpResourceProjectViewDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        if (!file.isValid) return

        if (file.name == "strings.xml" && file.path.contains("composeResources")) {
            val project = node.project ?: return
            val issueService = project.service<ResourceIssueService>()
            val count = issueService.countIssues(file)

            if (count > 0) {
                val issueText = " [$count Issues]"
                data.addText(issueText, SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, JBColor.ORANGE))
            }
        }
    }
}