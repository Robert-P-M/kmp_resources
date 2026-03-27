package dev.robdoes.kmpresources.presentation.editor

import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.core.application.service.ResourceUsageService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


class KmpResourceTableEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val repository: ResourceRepository
) : UserDataHolderBase(), FileEditor {


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

        project.messageBus.connect(this).subscribe(
            DumbService.DUMB_MODE,
            object : DumbService.DumbModeListener {
                override fun exitDumbMode() {
                    reloadTableData()
                }
            }
        )

        reloadTableData()
    }

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

            if (proceed) toggleUntranslatableUseCase(key, isUn)
            reloadTableData()
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


    private fun reloadTableData() {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            val resources = readAction { loadResourcesUseCase() }
            withEdtContext {
                tablePanel.updateData(resources)
            }
        }
    }

    private fun applyInlineEditAndReload(localeTag: String?, editAction: suspend () -> Unit) {
        project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.Default) {
            editAction()
            withContext(Dispatchers.EDT) {
                FileDocumentManager.getInstance().saveAllDocuments()
                file.parent?.parent?.refresh(true, true)
                reloadTableData()
                if (localeTag == null) KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
            }
        }
    }

    private fun triggerNativeFindUsages(keyName: String) {
        val normalizedKey = ResourceKeyNormalizer.normalize(keyName)
        val searchRegex = "Res\\.(string|plurals|array)\\.$normalizedKey|name=\"$keyName\""

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
    override fun dispose() {}
}

