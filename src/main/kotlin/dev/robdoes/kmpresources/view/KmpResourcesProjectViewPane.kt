package dev.robdoes.kmpresources.view

import com.intellij.icons.AllIcons
import com.intellij.ide.SelectInTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.Project
import dev.robdoes.kmpresources.KmpResourcesBundle
import javax.swing.Icon

class KmpResourcesProjectViewPane(project: Project) : ProjectViewPane(project) {
    companion object {
        const val ID = "KMP_RESOURCES_PROJECT_VIEW_PANE"
        const val HIDE_EMPTY_MODULES_KEY = "KMP_RESOURCES.HIDE_EMPTY_MODULES_KEY"
    }

    override fun getId(): String = ID

    override fun getTitle(): String = KmpResourcesBundle.message("project.view.pane.title")

    override fun getIcon(): Icon {
        return super.getIcon()
    }

    override fun getWeight(): Int = 100

    override fun addToolbarActions(actionGroup: DefaultActionGroup) {
        super.addToolbarActions(actionGroup)

        actionGroup
            .addAction(object: ToggleAction(
                KmpResourcesBundle.message("action.filter.empty.modules.text"),
                KmpResourcesBundle.message("action.filter.empty.modules.description"),
                AllIcons.General.Filter
            ){
                override fun getActionUpdateThread(): ActionUpdateThread {
                    return ActionUpdateThread.BGT
                }
                override fun isSelected(event: AnActionEvent): Boolean {
                    return PropertiesComponent.getInstance(myProject).getBoolean(HIDE_EMPTY_MODULES_KEY, false)
                }

                override fun setSelected(event: AnActionEvent, value: Boolean) {
                    PropertiesComponent.getInstance(myProject).setValue(HIDE_EMPTY_MODULES_KEY, value)
                    ProjectView.getInstance(myProject).refresh()
                }
            })
    }

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