package dev.robdoes.kmpresources.presentation.editor

import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.core.application.service.KmpResourceWorkspaceService
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.awaitSmartMode
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.*
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper
import dev.robdoes.kmpresources.presentation.editor.action.*
import dev.robdoes.kmpresources.presentation.editor.controller.KmpResourceTableEditorController
import dev.robdoes.kmpresources.presentation.editor.model.ResourceFilter
import dev.robdoes.kmpresources.presentation.editor.search.KmpUsageSearchScope
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceEditPanel
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceTablePanel
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * A specialized editor for managing KMP (Kotlin Multiplatform) resource tables within a project.
 * This editor provides functionality for editing, adding, and deleting resources, supporting features like
 * inline editing, filtering, and displaying resource usage.
 *
 * @constructor Creates an instance of the resource table editor.
 *
 * @param project The current IntelliJ project instance.
 * @param file The virtual file being edited.
 * @param repository The resource repository containing project resources.
 */
internal class KmpResourceTableEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val repository: ResourceRepository
) : UserDataHolderBase(), FileEditor {

    private val editorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val scannerService = project.service<ResourceUsageService>()

    private val loadResourcesUseCase = LoadResourcesUseCase(repository)
    private val deleteResourceUseCase = DeleteResourceUseCase(repository, loadResourcesUseCase)
    private val saveResourceUseCase = SaveResourceUseCase(repository)
    private val updateInlineStringUseCase = UpdateInlineStringUseCase(repository, loadResourcesUseCase)
    private val updateInlinePluralUseCase = UpdateInlinePluralUseCase(repository, loadResourcesUseCase)
    private val updateInlineArrayUseCase = UpdateInlineArrayUseCase(repository, loadResourcesUseCase)
    private val toggleUntranslatableUseCase = ToggleUntranslatableUseCase(repository)

    private val mainPanel = JPanel(BorderLayout())
    private val tablePanel = ResourceTablePanel(project, scannerService)
    private val editPanel = ResourceEditPanel(project)

    private var currentFilter = ResourceFilter.ALL
    private var currentEditingOldKey: String? = null

    private val controller = KmpResourceTableEditorController(
        project, file, scannerService, deleteResourceUseCase, saveResourceUseCase
    ).apply {
        onDataChanged = { reloadTableData() }
        onShowUsagesRequested = { triggerNativeFindUsages(it) }
    }

    init {
        setupToolbar()
        mainPanel.add(tablePanel, BorderLayout.CENTER)
        mainPanel.add(editPanel, BorderLayout.SOUTH)

        wireUpCallbacks()

        editorScope.launch {
            project.service<KmpResourceWorkspaceService>().getResourceStateFlow(file).collect {
                reloadTableData()
            }
        }

        project.messageBus.connect(this).subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun exitDumbMode() {
                    project.service<KmpProjectScopeService>().coroutineScope.launch {
                        project.service<KmpResourceWorkspaceService>().forceReload(file)
                    }
                }
            }
        )
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            project.awaitSmartMode()
            project.service<KmpResourceWorkspaceService>().forceReload(file)
        }


    }


    /**
     * Configures and binds the callback handlers for various user interactions within the resource table and editor panels.
     *
     * This method sets up the interaction logic between the table panel and the edit panel, handling operations such as:
     * - Resource editing
     * - Resource deletion
     * - Usage discovery
     * - Inline edits for strings, plurals, and arrays
     * - Toggling the translatability of resources
     * - Saving edited resources
     *
     * The callbacks ensure that actions are executed correctly within the project's coroutine scope and maintain
     * synchronization with the UI thread where necessary.
     *
     * Key handlers include:
     * - **Edit Requests**: Launches an edit operation on the selected resource.
     * - **Delete Requests**: Handles deletion for main resources and sub-items, delegating to the controller as needed.
     * - **Usage Requests**: Triggers find-usages functionality for the specific resource.
     * - **Inline Edits**: Applies inline modifications to strings, plurals, or arrays and reloads the affected data.
     * - **Translatability Toggles**: Prompts user confirmation for toggling a resource's translatability and updates it accordingly.
     * - **Save Requests**: Saves the resource changes and updates the UI to reflect the results.
     */
    private fun wireUpCallbacks() {

        tablePanel.onEditRequested = { key ->
            currentEditingOldKey = key
            project.service<KmpProjectScopeService>().coroutineScope.launch {
                val resource = readAction { loadResourcesUseCase().find { it.key == key } }
                withEdtContext { resource?.let { editPanel.showForUpdate(it) } }
            }
        }

        tablePanel.onDeleteRequested = { key, typeString, isSubItem ->
            project.service<KmpProjectScopeService>().coroutineScope.launch {
                val resource = readAction { loadResourcesUseCase().find { it.key == key } }
                if (resource != null) {
                    if (isSubItem) {
                        controller.handleSubItemDeletion(resource, typeString)
                    } else {
                        controller.handleMainResourceDeletion(resource)
                    }
                }
            }
        }

        tablePanel.onUsageRequested = { triggerNativeFindUsages(it) }

        tablePanel.onInlineStringEdited = { key, localeTag, isUn, newValue ->
            applyInlineEditAndReload(localeTag) {
                updateInlineStringUseCase(key, localeTag, isUn, newValue)
            }
        }

        tablePanel.onInlinePluralEdited = { key, localeTag, isUn, quantity, newValue ->
            applyInlineEditAndReload(localeTag) {
                updateInlinePluralUseCase(key, localeTag, isUn, quantity, newValue)
            }
        }

        tablePanel.onInlineArrayEdited = { key, localeTag, isUn, index, newValue ->
            applyInlineEditAndReload(localeTag) {
                updateInlineArrayUseCase(key, localeTag, isUn, index, newValue)
            }
        }

        tablePanel.onUntranslatableToggled = { key, isUn ->
            val proceed = if (isUn) {
                Messages.showYesNoDialog(
                    project,
                    KmpResourcesBundle.message("dialog.untranslatable.clean.message"),
                    KmpResourcesBundle.message("dialog.untranslatable.clean.title"),
                    Messages.getQuestionIcon()
                ) == Messages.YES
            } else true

            if (proceed) {
                applyInlineEditAndReload(null) {
                    toggleUntranslatableUseCase(key, isUn)
                }
            } else {
                reloadTableData()
            }
        }

        editPanel.onSaveRequested = { resourceToSave ->
            if (editPanel.isVisible && resourceToSave.key.isNotBlank()) {
                project.service<KmpProjectScopeService>().coroutineScope.launch {
                    val existing = readAction { loadResourcesUseCase().find { it.key == resourceToSave.key } }
                    val success = controller.handleResourceSave(resourceToSave, existing, currentEditingOldKey)

                    if (success) {
                        withEdtContext {
                            editPanel.isVisible = false
                            currentEditingOldKey = null
                            tablePanel.scrollToKey(resourceToSave.key)
                        }
                    }
                }
            }
        }
    }

    /**
     * Configures the toolbar for the resource editor panel.
     *
     * This method sets up an action toolbar with various actions to manage resources.
     * It includes actions for adding, removing, filtering, and syncing resources, as well
     * as a locale management feature. The toolbar is created using `DefaultActionGroup`
     * and registered with the main panel of the editor.
     *
     * The following actions are added to the toolbar:
     *
     * - **Add Resource Key**: Opens the editing panel to create a new resource key.
     * - **Remove Resource Key**: Removes the currently selected resource key from the table.
     * - **Sync Gradle**: Synchronizes the Gradle project with the resource files.
     * - **Filter Resources**: Applies a filter to the table to display a specific subset of resources.
     * - **Add Locale**: Adds a new locale to the project by invoking a coroutine in the project scope.
     *
     * Separators are included to visually group related actions for better usability. The toolbar
     * is bound to the `mainPanel` component for display.
     */
    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            add(AddResourceKeyAction {
                currentEditingOldKey = null
                editPanel.showForAdd()
            })
            add(
                RemoveResourceKeyAction(
                    hasSelection = { tablePanel.hasSelection() },
                    onRemoveRequested = { tablePanel.triggerDeleteForSelectedRow() }
                ))

            addSeparator()

            add(
                SyncGradleAction(
                    project = project,
                    file = file
                )
            )

            addSeparator()

            add(
                FilterResourceAction(
                    getCurrentFilter = { currentFilter },
                    onFilterSelected = { filter ->
                        currentFilter = filter
                        tablePanel.applyFilter(filter.name)
                    }
                ))

            addSeparator()
            add(AddLocaleAction { selectedLocale ->
                project.service<KmpProjectScopeService>().coroutineScope.launch {
                    controller.handleAddLocale(selectedLocale.languageTag)
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("KmpResourceEditorToolbar", actionGroup, true)
        toolbar.targetComponent = mainPanel
        mainPanel.add(toolbar.component, BorderLayout.NORTH)
    }


    /**
     * Reloads and updates the resource table data.
     *
     * This method fetches the latest resource data by invoking the `loadResourcesUseCase()` within a read action.
     * The retrieved resources are then used to update the table panel's data, ensuring synchronization with the UI
     * and executing updates on the Event Dispatch Thread (EDT) as required.
     *
     * Method execution is managed within a coroutine scope provided by the `KmpProjectScopeService`, ensuring proper
     * lifecycle management aligned with the project context.
     */
    private fun reloadTableData() {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            val resources = readAction { loadResourcesUseCase() }
            withEdtContext {
                tablePanel.updateData(resources)
            }
        }
    }

    /**
     * Applies an inline edit operation to resources and reloads the updated data in the project.
     *
     * This method executes a given editing action within a coroutine, ensuring proper lifecycle
     * management and synchronization with the IntelliJ project model. If the `localeTag` parameter
     * is null, it triggers the generation of Gradle accessors for the resource file, ensuring
     * updates are reflected in the build system.
     *
     * @param localeTag The locale tag of the resource being edited, or null if the edit is not locale-specific.
     * @param editAction A suspendable function representing the edit operation to be applied to the resources.
     */
    private fun applyInlineEditAndReload(localeTag: String?, editAction: suspend () -> Unit) {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            editAction()
            project.service<KmpResourceWorkspaceService>().forceReload(file)

            if (localeTag == null) {
                withContext(Dispatchers.EDT) {
                    CommandProcessor.getInstance().runUndoTransparentAction {
                        KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
                    }
                }
            }
        }
    }

    /**
     * Triggers the "Find Usages" functionality for resources matching the specified key name.
     *
     * This method normalizes the provided key name and constructs a regular expression
     * to locate references to the resource throughout the project.
     * The search focuses on Kotlin (`.kt`) and XML (`.xml`) files within the current project scope,
     * excluding build directories and generated files.
     *
     * @param keyName The name of the key whose usages need to be searched for.
     */
    private fun triggerNativeFindUsages(keyName: String) {
        val normalizedKey = ResourceKeyNormalizer.normalize(keyName)
        val searchRegex = "Res\\.(string|plurals|array)\\.$normalizedKey|name\\s*=\\s*[\"']$keyName[\"']"

        val findModel = FindModel().apply {
            stringToFind = searchRegex
            isRegularExpressions = true
            isCaseSensitive = true
            isProjectScope = false
            isCustomScope = true
            customScopeName = "KMP Usages ($keyName)"
            customScope = KmpUsageSearchScope(
                GlobalSearchScope.projectScope(project)
            )
        }

        FindInProjectManager.getInstance(project).startFindInProject(findModel)
    }


    /**
     * Scrolls the resource table to the row corresponding to the given key, selects the row,
     * and makes it visible within the viewport.
     *
     * This method searches through the resource table's model to locate the row where the
     * value in the "KEY" column matches the provided key. If a matching row is found, it updates
     * the table's selection, scrolls the row into view, and requests focus on the table.
     *
     * @param key The unique key corresponding to the row that needs to be scrolled to and selected.
     *            This key must match the value in the "KEY" column of the resource table model.
     */
    fun scrollToKey(key: String) {
        tablePanel.scrollToKey(key)
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = tablePanel
    override fun getName(): String = KmpResourcesBundle.message("ui.toolwindow.pane.title")
    override fun getFile(): VirtualFile = file
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {
        editorScope.cancel()
    }
}

