package dev.robdoes.kmpresources.presentation.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleProvider
import dev.robdoes.kmpresources.presentation.view.invalidateProjectViewCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

data class ModuleNodeData(val moduleName: String)
data class LocaleFileNodeData(
    val file: VirtualFile,
    val localeTag: String?,
    val displayName: String,
    val issueCount: Int
)

class KmpResourcesToolWindowPanel(
    private val project: Project,
    private val toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    init {
        setupToolbar()
        setupTree()
        add(JBScrollPane(tree), BorderLayout.CENTER)

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

    private fun setupTree() {
        tree.apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            cellRenderer = createTreeRenderer()

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) handleDoubleClick(e)
                }
            })
        }
    }

    private fun createTreeRenderer() = object : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: javax.swing.JTree, value: Any?, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            val userObject = (value as? DefaultMutableTreeNode)?.userObject ?: return

            when (userObject) {
                is ModuleNodeData -> {
                    icon = AllIcons.Nodes.Module
                    append(userObject.moduleName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }

                is LocaleFileNodeData -> {
                    val localeInfo = if (userObject.localeTag != null) {
                        LocaleProvider.getAvailableLocales().find { it.languageTag == userObject.localeTag }
                    } else null

                    icon = AllIcons.FileTypes.Xml

                    if (userObject.localeTag == null) {
                        append("default", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    } else {
                        val flag = localeInfo?.flagEmoji?.let { "$it " } ?: ""
                        val languageName = localeInfo?.displayName ?: userObject.displayName
                        append("$flag$languageName (${userObject.localeTag})", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }

                    if (userObject.issueCount > 0) {
                        val issueSuffix = KmpResourcesBundle.message("ui.toolwindow.issue.suffix.issues")
                        append(" (${userObject.issueCount} $issueSuffix)", SimpleTextAttributes.ERROR_ATTRIBUTES)
                    } else if (userObject.issueCount == 0) {
                        val okText = KmpResourcesBundle.message("ui.toolwindow.issue.suffix.ok")
                        append(" ($okText)", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN))
                    }
                }
            }
        }
    }

    private fun handleDoubleClick(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val data = node.userObject as? LocaleFileNodeData ?: return

        FileEditorManager.getInstance(project).openFile(data.file, true)
    }

    fun refreshData() {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            DumbService.getInstance(project).waitForSmartMode()
            val issueService = project.service<ResourceIssueService>()
            val files = issueService.findAllResourceFiles()

            val structure = mutableMapOf<String, MutableList<LocaleFileNodeData>>()

            for (file in files) {
                val issueCount = issueService.countIssues(file)

                val modulePath = file.path
                    .substringAfter(project.basePath ?: "")
                    .substringBefore("/src/")
                    .replace("/", ":")
                    .removePrefix(":")

                val folderName = file.parent.name
                val localeTag = if (folderName == "values") null else folderName.substringAfter("values-")
                val displayLocale = localeTag ?: "default"

                structure.getOrPut(modulePath) { mutableListOf() }
                    .add(LocaleFileNodeData(file, localeTag, displayLocale, issueCount))
            }

            withContext(Dispatchers.EDT) {
                rootNode.removeAllChildren()

                structure.keys.sorted().forEach { modulePath ->
                    val moduleNode = DefaultMutableTreeNode(ModuleNodeData(modulePath))

                    structure[modulePath]?.sortedBy { it.localeTag ?: "" }?.forEach { localeData ->
                        moduleNode.add(DefaultMutableTreeNode(localeData))
                    }
                    rootNode.add(moduleNode)
                }

                treeModel.nodeStructureChanged(rootNode)

                for (i in 0 until tree.rowCount) tree.expandRow(i)
            }
        }
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
            })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar("KmpResourcesToolbar", actionGroup, true)
        toolbar.targetComponent = this
        add(toolbar.component, BorderLayout.NORTH)
    }
}