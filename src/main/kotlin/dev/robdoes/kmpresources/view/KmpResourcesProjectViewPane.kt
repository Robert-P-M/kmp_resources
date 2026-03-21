package dev.robdoes.kmpresources.view

import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.project.Project
import javax.swing.Icon

class KmpResourcesProjectViewPane(project: Project) : ProjectViewPane(project) {
    companion object {
        const val ID = "KMP_RESOURCES_PROJECT_VIEW_PANE"
    }

    override fun getId(): String = ID

    override fun getTitle(): String = "KMP Resources"

    override fun getIcon(): Icon {
        return super.getIcon()
    }

    override fun getWeight(): Int = 100

    override fun createSelectInTarget(): SelectInTarget {
        return object : SelectInTarget {
            override fun canSelect(context: com.intellij.ide.SelectInContext?) = false
            override fun selectIn(context: com.intellij.ide.SelectInContext?, requestFocus: Boolean) {}
            override fun getToolWindowId() = ID
            override fun getMinorViewId() = null
            override fun getWeight() = 0f
        }
    }
}