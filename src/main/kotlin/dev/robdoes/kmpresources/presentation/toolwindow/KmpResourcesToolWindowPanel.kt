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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import dev.robdoes.kmpresources.core.application.service.ResourceIssueService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.awaitSmartMode
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
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Represents data for a module node in the UI tree of the KMP Resources Tool Window.
 *
 * This class is used as the user object for tree nodes that correspond to modules
 * in a Kotlin Multiplatform project. It holds the name of the module that the node represents.
 *
 * @property moduleName The name of the module associated with this node.
 */
internal data class ModuleNodeData(val moduleName: String)

/**
 * Represents metadata associated with a locale-specific resource file in the KMP Resources Tool Window.
 *
 * This data class links a virtual resource file to its locale-specific details for use in a tree-based UI.
 *
 * Key properties include:
 * - `file`: The virtual file associated with the locale.
 * - `localeTag`: An optional BCP 47 tag describing the locale (e.g., "en-US").
 * - `displayName`: The human-readable name associated with the file or locale.
 *
 * Typical use cases include:
 * - Representing locale resource files in a tree structure.
 * - Supporting actions such as displaying or opening locale-based resource files in the UI.
 */
internal data class LocaleFileNodeData(
    val file: VirtualFile,
    val localeTag: String?,
    val displayName: String,
)

/**
 * Represents the data model for a node in the resource view of the KMP Resources Tool Window.
 *
 * This class is used to encapsulate metadata about a resource view node, enabling its display and interaction
 * within the tool window. It provides information such as the associated module path, the default file for the node,
 * the number of unused keys, and whether there are missing translations in the resource.
 *
 * @property modulePath The path of the module associated with this resource view node.
 * @property defaultFile An optional reference to the default file associated with this node.
 * @property unusedKeysCount The number of resource keys that are unused within this module.
 * @property hasMissingTranslations A flag indicating whether there are missing translations for this module's resources.
 */
internal data class ResourceViewNodeData(
    val modulePath: String,
    val defaultFile: VirtualFile?,
    val unusedKeysCount: Int,
    val hasMissingTranslations: Boolean
)

/**
 * This class represents the main panel of the KMP Resources Tool Window in the IDE.
 * It is responsible for displaying and managing resource-related data for multiplatform projects.
 *
 * The panel provides the following functionalities:
 * - A tree view that displays the structure and details of modules, resources, and locales.
 * - Dynamic updates of resource data based on external file system changes or IDE events.
 * - Integration with the "Dumb Mode", ensuring compatibility during IDE indexing.
 * - Opening resource files or resource views in the IDE when nodes in the tree are double-clicked.
 *
 * @constructor
 * Initializes the panel with the provided project and tool window instance.
 * The panel supports lazy-loading of resource data and reflects changes dynamically.
 *
 * @param project The current project instance where the tool window is displayed.
 * @param toolWindow The tool window in which this panel is embedded.
 */
internal class KmpResourcesToolWindowPanel(
    private val project: Project,
    toolWindow: ToolWindow
) : JPanel(BorderLayout()) {

    private val rootNode = DefaultMutableTreeNode("Root")
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    init {
        setupToolbar()
        setupTree()
        add(JBScrollPane(tree), BorderLayout.CENTER)

        val connection = project.messageBus.connect(toolWindow.disposable)

        connection.subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun exitDumbMode() {
                    invalidateProjectViewCache()
                    ProjectView.getInstance(project).refresh()
                    refreshData()
                }
            }
        )
        connection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val isKmpResourceChanged = events.any { event ->
                        val changedFile = event.file ?: return@any false
                        changedFile.path.contains("/composeResources/values") && changedFile.extension == "xml"
                    }
                    if (isKmpResourceChanged) {
                        invalidateProjectViewCache()
                        ProjectView.getInstance(project).refresh()
                        refreshData()
                    }
                }
            }
        )

        DumbService.getInstance(project).runWhenSmart {
            refreshData()
        }
    }

    /**
     * Configures the tree component used within the tool window panel.
     *
     * The method sets up various properties and listeners for the tree, ensuring it behaves
     * as intended for the resources navigation and interaction. It adjusts visibility settings,
     * selection modes, and provides a custom renderer for the tree nodes.
     *
     * Key functionalities:
     * - Hides the root node from being directly visible in the tree.
     * - Enables root handles for better expand/collapse control.
     * - Sets the selection mode to only allow single node selection at a time.
     * - Assigns a custom tree renderer that customizes the appearance of nodes depending on their types.
     * - Adds a mouse listener to detect double-click actions, delegating to the `handleDoubleClick` method
     *   to handle specific interactions with tree nodes.
     */
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

    /**
     * Creates a custom tree cell renderer for the resource tree in the KMP Resources Tool Window.
     *
     * This method returns an instance of `ColoredTreeCellRenderer` configured to customize the appearance
     * of tree nodes based on the type of data they represent. It checks the userObject of each node
     * and adjusts its icon and text display accordingly.
     *
     * Customizations include:
     * - **ModuleNodeData**: Displays an icon for modules along with the module name styled in bold.
     * - **ResourceViewNodeData**: Displays an icon for resource views and appends status information (e.g., unused keys, missing translations)
     *   with styled color feedback (green, orange, red) to indicate resource health.
     * - **LocaleFileNodeData**: Displays an icon for locale files, the locale's human-readable name, and its associated BCP 47 tag.
     *   Flags and default names are included where appropriate.
     *
     * The renderer ensures that each resource tree node is visually distinguishable and provides context-specific
     * information directly within the tree view.
     *
     * @return A customized implementation of `ColoredTreeCellRenderer` for rendering resource tree nodes.
     */
    private fun createTreeRenderer() = object : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean,
            expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean
        ) {
            val userObject = (value as? DefaultMutableTreeNode)?.userObject ?: return

            when (userObject) {
                is ModuleNodeData -> {
                    icon = AllIcons.Nodes.Module
                    append(userObject.moduleName, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }

                is ResourceViewNodeData -> {
                    icon = AllIcons.Nodes.DataTables
                    append(
                        KmpResourcesBundle.message("ui.toolwindow.node.resource_view"),
                        SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                    )

                    if (userObject.unusedKeysCount == 0 && !userObject.hasMissingTranslations) {
                        val okText = KmpResourcesBundle.message("ui.toolwindow.issue.suffix.ok")
                        append(" ($okText)", SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.GREEN))
                    } else {
                        append(" (", SimpleTextAttributes.REGULAR_ATTRIBUTES)

                        if (userObject.hasMissingTranslations) {
                            val missingText = KmpResourcesBundle.message("ui.toolwindow.issue.missing_translations")
                            append(missingText, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, JBColor.ORANGE))
                        }

                        if (userObject.hasMissingTranslations && userObject.unusedKeysCount > 0) {
                            append(" | ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                        }

                        if (userObject.unusedKeysCount > 0) {
                            val unusedText = KmpResourcesBundle.message(
                                "ui.toolwindow.issue.unused_keys",
                                userObject.unusedKeysCount
                            )
                            append(unusedText, SimpleTextAttributes.ERROR_ATTRIBUTES)
                        }

                        append(")", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    }
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
                }
            }
        }
    }

    /**
     * Handles double-click events on the resource tree nodes within the KMP Resources Tool Window.
     *
     * The method determines the type of the clicked tree node and performs appropriate actions
     * based on the node's data. If the node represents a locale file or a resource view, it opens
     * the associated file in the editor.
     *
     * - For nodes containing `LocaleFileNodeData`, their associated virtual file is opened.
     * - For nodes containing `ResourceViewNodeData`, a custom `KmpResourceVirtualFile` is created
     *   and opened in the editor to represent the resource data in a read-only context.
     *
     * @param e The `MouseEvent` triggered by a double-click on a tree node. It contains information
     *           about the mouse pointer's location and the event's context.
     */
    private fun handleDoubleClick(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val fileEditorManager = FileEditorManager.getInstance(project)

        when (val data = node.userObject) {
            is LocaleFileNodeData -> {
                fileEditorManager.openFile(data.file, true)
            }

            is ResourceViewNodeData -> {
                val file = data.defaultFile ?: return

                val kmpVirtualFile = dev.robdoes.kmpresources.presentation.editor.KmpResourceVirtualFile(
                    data.modulePath,
                    file
                )

                fileEditorManager.openFile(kmpVirtualFile, true)
            }
        }
    }

    /**
     * Refreshes and updates the resource tree within the KMP Resources Tool Window panel.
     *
     * This method retrieves all resource files within the project's defined scope, analyzes their structure and issues,
     * and reorganizes the resource tree to reflect the updated state. It is executed asynchronously using coroutines
     * to ensure smooth integration with the UI thread, avoiding blocking operations.
     *
     * Key tasks performed by this method include:
     * - Retrieving all resource files from the project using the `ResourceIssueService`.
     * - Grouping and organizing files by their module paths and locale data (including default and localized files).
     * - Calculating resource statistics such as unused keys and missing translations.
     * - Updates the resource tree UI on the EDT (Event Dispatch Thread) to ensure consistent rendering of the tree structure.
     *
     * The refreshed tree structure highlights modules, default locale files, and localized resources in a hierarchical view,
     * along with associated statistics such as resource usage and translation completeness.
     */
    fun refreshData() {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            project.awaitSmartMode()

            val issueService = project.service<ResourceIssueService>()
            val files = issueService.findAllResourceFiles()

            if (files.isEmpty()) return@launch

            val structure = mutableMapOf<String, MutableList<LocaleFileNodeData>>()
            val moduleIssues = mutableMapOf<String, Int>()
            val missingTranslationsMap = mutableMapOf<String, Boolean>()

            for (file in files) {
                val modulePath = file.path
                    .substringAfter(project.basePath ?: "")
                    .substringBefore("/src/")
                    .replace("/", ":")
                    .removePrefix(":")

                val folderName = file.parent.name
                val localeTag = if (folderName == "values") null else folderName.substringAfter("values-")
                val displayLocale = localeTag ?: "default"

                structure.getOrPut(modulePath) { mutableListOf() }
                    .add(LocaleFileNodeData(file, localeTag, displayLocale))

                if (localeTag == null) {
                    moduleIssues[modulePath] = issueService.countIssues(file)
                }
            }

            for ((modulePath, nodes) in structure) {
                val defaultFile = nodes.find { it.localeTag == null }?.file
                val localizedFiles = nodes.filter { it.localeTag != null }.map { it.file }

                if (defaultFile != null && localizedFiles.isNotEmpty()) {
                    missingTranslationsMap[modulePath] =
                        issueService.hasMissingTranslations(defaultFile, localizedFiles)
                } else {
                    missingTranslationsMap[modulePath] = false
                }
            }


            withContext(Dispatchers.EDT) {
                rootNode.removeAllChildren()

                structure.keys.sorted().forEach { modulePath ->
                    val moduleNode = DefaultMutableTreeNode(ModuleNodeData(modulePath))
                    val defaultLocaleData = structure[modulePath]?.find { it.localeTag == null }

                    val issues = moduleIssues[modulePath] ?: 0
                    val hasMissing = missingTranslationsMap[modulePath] ?: false

                    moduleNode.add(
                        DefaultMutableTreeNode(
                            ResourceViewNodeData(
                                modulePath = modulePath,
                                defaultFile = defaultLocaleData?.file,
                                unusedKeysCount = issues,
                                hasMissingTranslations = hasMissing
                            )
                        )
                    )

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

    /**
     * Sets up the toolbar for the KMP Resources Tool Window Panel.
     *
     * This method creates a toolbar with actions specific to the resources tool window,
     * including a refresh action to update the resource tree and invalidate cached data.
     *
     * Key actions and functionalities:
     * - **Refresh Action**: Adds a button with an icon and description for refreshing the resource tree.
     *   - Clears the project view cache to ensure updated data.
     *   - Refreshes the `ProjectView` to reflect any changes in the resource hierarchy.
     *   - Calls the `refreshData` method to rebuild the resource tree with up-to-date information.
     *
     * The toolbar is created using the `ActionManager` and is configured to align with the tool window's
     * user interface using a `BorderLayout` at the top of the panel.
     */
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