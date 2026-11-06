package dev.robdoes.kmpresources.presentation.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * A factory class responsible for creating and initializing the content of the KMP Resources Tool Window.
 * Implements the `ToolWindowFactory` and `DumbAware` interfaces to integrate with the IntelliJ Platform.
 *
 * This Tool Window is designed to display resources specific to Kotlin Multiplatform Projects (KMP).
 *
 * Key responsibilities:
 * - Create the content to be displayed within the tool window.
 * - Leverage the `KmpResourcesToolWindowPanel` to provide the main UI for the tool window.
 * - Manage interaction between the IntelliJ Platform's `ToolWindow` system and the custom panel.
 *
 * @constructor Creates an instance of `KmpResourcesToolWindowFactory`.
 * @see ToolWindowFactory
 * @see DumbAware
 */
internal class KmpResourcesToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = KmpResourcesToolWindowPanel(project, toolWindow)

        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}