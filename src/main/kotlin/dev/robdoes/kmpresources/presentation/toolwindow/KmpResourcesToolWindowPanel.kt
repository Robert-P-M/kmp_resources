package dev.robdoes.kmpresources.presentation.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
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
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.service.ResourceIssueService
import dev.robdoes.kmpresources.presentation.view.invalidateProjectViewCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

data class ResourceFileItem(val file: VirtualFile, val issueCount: Int = -1)

class KmpResourcesToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    private val listModel = DefaultListModel<ResourceFileItem>()
    private val issueList = JBList(listModel)

    init {
        setupToolbar()
        setupList()
        add(JBScrollPane(issueList), BorderLayout.CENTER)
        project.messageBus.connect(toolWindow.disposable).subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun exitDumbMode() {
                    invalidateProjectViewCache()
                    ProjectView.getInstance(project).refresh()
                    refreshData()
                }
            }
        )
        refreshData()
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(
                KmpResourcesBundle.message("action.toolwindow.refresh.text"),
                KmpResourcesBundle.message("action.toolwindow.refresh.desc"),
                AllIcons.Actions.Refresh
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    invalidateProjectViewCache()

                    ProjectView.getInstance(project).refresh()

                    refreshData()
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            })
        }

        val toolbar =
            ActionManager.getInstance().createActionToolbar("KmpResourcesToolWindow", actionGroup, true).apply {
                targetComponent = this@KmpResourcesToolWindowPanel
            }
        add(toolbar.component, BorderLayout.NORTH)
    }

    private fun setupList() {
        issueList.apply {
            emptyText.text = KmpResourcesBundle.message("ui.toolwindow.status.scanning")
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = createCellRenderer()

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 1) handleListClick(e)
                }
            })
        }
    }

    private fun createCellRenderer() = object : ColoredListCellRenderer<ResourceFileItem>() {
        override fun customizeCellRenderer(
            list: JList<out ResourceFileItem>,
            value: ResourceFileItem,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            val file = value.file
            val displayPath = file.path.substringAfter(project.basePath ?: "").substringBefore("/composeResources")

            icon = file.fileType.icon
            append(displayPath, SimpleTextAttributes.REGULAR_ATTRIBUTES)
            append(" / ${file.name}", SimpleTextAttributes.GRAY_ATTRIBUTES)

            when {
                value.issueCount == -1 -> append(
                    " (${KmpResourcesBundle.message("ui.toolwindow.status.scanning")})",
                    SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES
                )

                value.issueCount > 0 -> append(
                    " (${value.issueCount} ${KmpResourcesBundle.message("ui.toolwindow.issue.suffix.issues")})",
                    SimpleTextAttributes.ERROR_ATTRIBUTES
                )

                else -> append(
                    " (${KmpResourcesBundle.message("ui.toolwindow.issue.suffix.ok")})",
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN)
                )
            }
        }
    }

    private fun handleListClick(e: MouseEvent) {
        val index = issueList.locationToIndex(e.point)
        if (index >= 0 && issueList.getCellBounds(index, index)?.contains(e.point) == true) {
            listModel.getElementAt(index)?.file?.let {
                FileEditorManager.getInstance(project).openFile(it, true)
            }
        }
    }

    fun refreshData() {
        listModel.clear()
        issueList.emptyText.text = KmpResourcesBundle.message("ui.toolwindow.status.waiting_indexing")
        toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowInspection)

        project.service<KmpProjectScopeService>().coroutineScope.launch {

            DumbService.getInstance(project).waitForSmartMode()

            val issueService = project.service<ResourceIssueService>()
            val files = issueService.findAllResourceFiles()

            if (files.isEmpty()) {
                withContext(Dispatchers.EDT) {
                    issueList.emptyText.text = KmpResourcesBundle.message("ui.toolwindow.empty.no_files")
                }
                return@launch
            }

            var totalIssues = 0
            val evaluatedItems = mutableListOf<ResourceFileItem>()

            for (file in files) {
                val count = issueService.countIssues(file)
                totalIssues += count
                evaluatedItems.add(ResourceFileItem(file, count))
            }

            withContext(Dispatchers.EDT) {
                listModel.clear()
                evaluatedItems.forEach { listModel.addElement(it) }

                if (totalIssues > 0) {
                    val errorIcon = LayeredIcon(2).apply {
                        setIcon(AllIcons.Toolwindows.ToolWindowInspection, 0)
                        setIcon(AllIcons.Nodes.ErrorMark, 1, SwingConstants.SOUTH_EAST)
                    }
                    toolWindow.setIcon(errorIcon)
                } else {
                    toolWindow.setIcon(AllIcons.General.InspectionsOK)
                }
            }
        }
    }
}
