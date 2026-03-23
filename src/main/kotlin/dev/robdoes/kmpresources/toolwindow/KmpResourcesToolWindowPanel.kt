package dev.robdoes.kmpresources.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.LayeredIcon
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import dev.robdoes.kmpresources.KmpResourcesBundle
import dev.robdoes.kmpresources.service.ResourceIssueService
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

data class ResourceFileItem(val file: VirtualFile, val issueCount: Int = -1)

class KmpResourcesToolWindowPanel(private val project: Project, private val toolWindow: ToolWindow) :
    JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<ResourceFileItem>()
    private val issueList = JBList(listModel)

    init {
        setupToolbar()
        setupList()
        add(JBScrollPane(issueList), BorderLayout.CENTER)
        refreshData()
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction(
            KmpResourcesBundle.message("toolwindow.action.refresh.text"),
            KmpResourcesBundle.message("toolwindow.action.refresh.desc"),
            AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                refreshData()
            }
        })

        val toolbar = ActionManager.getInstance().createActionToolbar("KmpResourcesToolWindow", actionGroup, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)
    }

    private fun setupList() {
        issueList.emptyText.text = KmpResourcesBundle.message("status.tooltip.analyzing")
        issueList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        issueList.cellRenderer = object : ColoredListCellRenderer<ResourceFileItem>() {
            override fun customizeCellRenderer(
                list: JList<out ResourceFileItem>,
                value: ResourceFileItem,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                val file = value.file
                val count = value.issueCount
                val displayPath = file.path.substringAfter(project.basePath ?: "").substringBefore("/composeResources")

                icon = file.fileType.icon
                append(displayPath, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                append(" / ${file.name}", SimpleTextAttributes.GRAY_ATTRIBUTES)

                if (count == -1) {
                    append(
                        " (${KmpResourcesBundle.message("toolwindow.status.scanning")})",
                        SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
                    )
                } else if (count > 0) {
                    append(
                        " ($count ${KmpResourcesBundle.message("toolwindow.issue.suffix.issues")})",
                        SimpleTextAttributes.ERROR_ATTRIBUTES
                    )
                } else {
                    append(
                        " (${KmpResourcesBundle.message("toolwindow.issue.suffix.ok")})",
                        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN)
                    )
                }
            }
        }

        issueList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) {
                    val index = issueList.locationToIndex(e.point)
                    if (index >= 0) {
                        val bounds = issueList.getCellBounds(index, index)
                        if (bounds != null && bounds.contains(e.point)) {
                            val selectedItem = listModel.getElementAt(index)
                            FileEditorManager.getInstance(project).openFile(selectedItem.file, true)
                        }
                    }
                }
            }
        })
    }

    fun refreshData() {
        listModel.clear()
        issueList.emptyText.text = KmpResourcesBundle.message("toolwindow.status.waiting_indexing")

        ApplicationManager.getApplication().invokeLater {
            toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowInspection)
        }

        DumbService.getInstance(project).runWhenSmart {
            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : Task.Backgroundable(
                    project,
                    KmpResourcesBundle.message("toolwindow.task.scanning_title"),
                    true
                ) {

                    override fun run(indicator: ProgressIndicator) {
                        val issueService = project.service<ResourceIssueService>()

                        indicator.text = KmpResourcesBundle.message("toolwindow.task.finding_files")
                        val files = issueService.findAllResourceFiles()

                        if (files.isEmpty()) {
                            ApplicationManager.getApplication().invokeLater {
                                issueList.emptyText.text = KmpResourcesBundle.message("toolwindow.empty.no_files")
                            }
                            return
                        }

                        ApplicationManager.getApplication().invokeLater {
                            files.forEach { listModel.addElement(ResourceFileItem(it, -1)) }
                        }

                        var totalIssues = 0

                        for (i in files.indices) {
                            if (indicator.isCanceled) break

                            val file = files[i]
                            indicator.text = KmpResourcesBundle.message("toolwindow.task.analyzing_file", file.name)
                            val count = issueService.countIssues(file)
                            totalIssues += count

                            ApplicationManager.getApplication().invokeLater {
                                if (i < listModel.size) {
                                    val currentItem = listModel.getElementAt(i)
                                    if (currentItem.file == file) {
                                        listModel.setElementAt(currentItem.copy(issueCount = count), i)
                                    }
                                }
                            }
                        }

                        ApplicationManager.getApplication().invokeLater {
                            if (totalIssues > 0) {
                                val errorIcon = LayeredIcon(2)
                                errorIcon.setIcon(AllIcons.Toolwindows.ToolWindowInspection, 0)
                                errorIcon.setIcon(AllIcons.Nodes.ErrorMark, 1, SwingConstants.SOUTH_EAST)
                                toolWindow.setIcon(errorIcon)
                            } else {
                                toolWindow.setIcon(AllIcons.General.InspectionsOK)
                            }
                        }
                    }
                }
            )
        }
    }
}