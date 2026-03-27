package dev.robdoes.kmpresources.presentation.editor

import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.service.ResourceScannerService
import dev.robdoes.kmpresources.data.repository.LocaleRepositoryFactory
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.model.XmlResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.*
import dev.robdoes.kmpresources.ide.refactoring.KmpResourceRefactorService
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper
import dev.robdoes.kmpresources.presentation.editor.action.AddLocaleAction
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceEditPanel
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceTablePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel


enum class ResourceFilter(val bundleKey: String) {
    ALL("action.table.filter.all"),
    STRINGS("action.table.filter.strings"),
    PLURALS("action.table.filter.plurals"),
    ARRAYS("action.table.filter.arrays");

    fun getDisplayText(): String = KmpResourcesBundle.message(bundleKey)
}

class KmpResourceTableEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val repository: ResourceRepository
) : UserDataHolderBase(), FileEditor {

    private val scannerService = project.service<ResourceScannerService>()

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
                withEdtContext {
                    resource?.let { editPanel.showForUpdate(it) }
                }
            }
        }

        tablePanel.onDeleteRequested = { key, type, isSubItem ->
            if (isSubItem) {
                handleSubItemDeletion(key, type)
            } else {
                handleMainResourceDeletion(key, type)
            }
        }

        tablePanel.onUsageRequested = { triggerNativeFindUsages(it) }

        tablePanel.onInlineStringEdited = { key, localeTag, isUn, newValue ->
            updateInlineStringUseCase(key, localeTag, isUn, newValue)
            if (localeTag == null) {
                KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
            }
        }

        tablePanel.onInlinePluralEdited = { key, localeTag, isUn, quantity, newValue ->
            updateInlinePluralUseCase(key, localeTag, isUn, quantity, newValue)
            if (localeTag == null) {
                KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
            }
        }

        tablePanel.onInlineArrayEdited = { key, localeTag, isUn, index, newValue ->
            updateInlineArrayUseCase(key, localeTag, isUn, index, newValue)
            reloadTableData()
            if (localeTag == null) {
                KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
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
                toggleUntranslatableUseCase(key, isUn)
                reloadTableData()
            } else {
                reloadTableData()
            }
        }

        editPanel.onSaveRequested = { resourceToSave ->
            if (editPanel.isVisible && resourceToSave.key.isNotBlank()) {
                handleResourceSave(resourceToSave)
            }
        }
    }

    private fun handleSubItemDeletion(key: String, type: String) {
        val isPlural = !type.startsWith("item[")
        val proceed = if (isPlural) {
            Messages.showYesNoDialog(
                project,
                KmpResourcesBundle.message("dialog.delete.plural.message", type, key),
                KmpResourcesBundle.message("dialog.delete.plural.title"),
                Messages.getQuestionIcon()
            ) == Messages.YES
        } else true

        if (proceed) {
            deleteResourceUseCase(key, type, true)
            reloadTableData()
            KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
        }
    }

    private fun handleMainResourceDeletion(key: String, type: String) {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val isUsed = scannerService.isResourceUsed(key)

            withContext(Dispatchers.EDT) {
                if (!isUsed) {
                    if (Messages.showYesNoDialog(
                            project,
                            KmpResourcesBundle.message("dialog.delete.resource.message", key),
                            KmpResourcesBundle.message("dialog.delete.resource.title"),
                            Messages.getQuestionIcon()
                        ) == Messages.YES
                    ) {
                        deleteResourceUseCase(key, type, false)
                        reloadTableData()
                        KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
                    }
                } else {
                    if (Messages.showDialog(
                            project,
                            KmpResourcesBundle.message("dialog.warning.in_use.message", key),
                            KmpResourcesBundle.message("dialog.warning.in_use.title"),
                            arrayOf(
                                KmpResourcesBundle.message("dialog.btn.show_usages"),
                                KmpResourcesBundle.message("dialog.btn.cancel")
                            ),
                            0,
                            Messages.getWarningIcon()
                        ) == 0
                    ) {
                        triggerNativeFindUsages(key)
                    }
                }
            }
        }
    }

    private fun handleResourceSave(resourceToSave: XmlResource) {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val existing = readAction { loadResourcesUseCase().find { it.key == resourceToSave.key } }

            withEdtContext {
                if (existing != null && existing.xmlTag != resourceToSave.xmlTag && currentEditingOldKey != resourceToSave.key) {
                    Messages.showErrorDialog(
                        project,
                        KmpResourcesBundle.message("dialog.error.key.exists", resourceToSave.key),
                        KmpResourcesBundle.message("dialog.error.title")
                    )
                    return@withEdtContext
                }
            }
            withContext(Dispatchers.EDT) {


                val oldKey = currentEditingOldKey
                val type = when (resourceToSave) {
                    is StringResource -> "string"
                    is PluralsResource -> "plurals"
                    is StringArrayResource -> "string-array"
                }
                if (oldKey != null && oldKey != resourceToSave.key) {
                    KmpResourceRefactorService.renameKeyInModule(project, file, type, oldKey, resourceToSave.key)
                }
                withEdtContext {
                    saveResourceUseCase(resourceToSave)
                    editPanel.isVisible = false
                    currentEditingOldKey = null
                    reloadTableData()
                    tablePanel.scrollToKey(resourceToSave.key)
                    KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
                }
            }
        }
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup().apply {
            addAction(object : AnAction(
                KmpResourcesBundle.message("action.table.add.key.text"),
                KmpResourcesBundle.message("action.table.add.key.desc"),
                AllIcons.General.Add
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    currentEditingOldKey = null
                    editPanel.showForAdd()
                }
            })

            addAction(object : AnAction(
                KmpResourcesBundle.message("action.table.remove.key.text"),
                KmpResourcesBundle.message("action.table.remove.key.desc"),
                AllIcons.General.Remove
            ) {
                override fun actionPerformed(e: AnActionEvent) = tablePanel.triggerDeleteForSelectedRow()
                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = tablePanel.hasSelection()
                }

                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })

            addSeparator()

            addAction(object : AnAction(
                KmpResourcesBundle.message("action.sync.gradle.text"),
                KmpResourcesBundle.message("action.sync.gradle.desc"),
                AllIcons.Actions.Refresh
            ) {
                override fun actionPerformed(e: AnActionEvent) =
                    KmpGradleSyncHelper.triggerGenerateAccessors(project, file)
            })

            addSeparator()

            val filterAction = object : ComboBoxAction() {
                override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
                    return DefaultActionGroup().apply {
                        ResourceFilter.entries.forEach { filter ->
                            addAction(object : AnAction(filter.getDisplayText()) {
                                override fun actionPerformed(e: AnActionEvent) {
                                    currentFilter = filter
                                    tablePanel.applyFilter(filter.name)
                                }
                            })
                        }
                    }
                }

                override fun update(e: AnActionEvent) {
                    super.update(e)
                    e.presentation.text = currentFilter.getDisplayText()
                }

                override fun getActionUpdateThread() = ActionUpdateThread.BGT
            }
            addAction(filterAction)

            addSeparator()
            addAction(AddLocaleAction { selectedLocale ->
                val localeFactory = project.service<LocaleRepositoryFactory>()
                val localeRepo = localeFactory.createLocaleRepository()
                val addLocaleUseCase = AddLocaleUseCase(
                    localeRepository = localeRepo,
                    resourceRepositoryFactory = localeFactory.resourceRepositoryFactory()
                )

                project.service<KmpProjectScopeService>().coroutineScope.launch {
                    try {
                        addLocaleUseCase(selectedLocale.languageTag)

                        withEdtContext {
                            reloadTableData()
                        }
                    } catch (e: Exception) {
                        withEdtContext {
                            Messages.showErrorDialog(
                                project,
                                e.message ?: "Failed to add locale ${selectedLocale.languageTag}",
                                KmpResourcesBundle.message("dialog.error.title")
                            )
                        }
                    }
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("KmpResourceEditorToolbar", actionGroup, true)
        toolbar.targetComponent = mainPanel
        mainPanel.add(toolbar.component, BorderLayout.NORTH)
    }


    private fun reloadTableData() {
        project.service<KmpProjectScopeService>().coroutineScope.launch {
            val resources = readAction { loadResourcesUseCase() }

            withEdtContext {
                tablePanel.updateData(resources)
            }
        }
    }

    private fun triggerNativeFindUsages(keyName: String) {
        val normalizedKey = keyName.replace(".", "_").replace("-", "_")

        val searchRegex = "Res\\.(string|plurals|array)\\.$normalizedKey|name=\"$keyName\""

        val findModel = com.intellij.find.FindModel().apply {
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

class KmpUsageSearchScope(baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(baseScope) {

    override fun contains(file: VirtualFile): Boolean {
        val path = file.path
        val extension = file.extension?.lowercase()

        if (extension != "kt" && extension != "xml") return false

        return !path.contains("/generated/") &&
                !path.contains("/build/") &&
                super.contains(file)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KmpUsageSearchScope
        return myBaseScope == other.myBaseScope
    }

    override fun hashCode(): Int = myBaseScope.hashCode()

    override fun toString() = KmpResourcesBundle.message("search.scope.name")
}