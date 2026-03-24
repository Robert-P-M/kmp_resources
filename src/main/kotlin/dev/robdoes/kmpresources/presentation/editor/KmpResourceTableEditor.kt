package dev.robdoes.kmpresources.presentation.editor

import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.util.concurrency.AppExecutorUtil
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.service.ResourceScannerService
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.domain.repository.ResourceRepository
import dev.robdoes.kmpresources.domain.usecase.*
import dev.robdoes.kmpresources.ide.refactoring.KmpResourceRefactorService
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceEditPanel
import dev.robdoes.kmpresources.presentation.editor.ui.ResourceTablePanel
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class KmpResourceTableEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val repository: ResourceRepository
) : UserDataHolderBase(), FileEditor {

    private val scannerService = project.service<ResourceScannerService>()


    private val loadResourcesUseCase = LoadResourcesUseCase(repository)

    private val deleteResourceUseCase = DeleteResourceUseCase(repository, loadResourcesUseCase)
    private val saveResourceUseCase = SaveResourceUseCase(repository)
    private val updateInlineStringUseCase = UpdateInlineStringUseCase(repository)
    private val updateInlinePluralUseCase = UpdateInlinePluralUseCase(repository, loadResourcesUseCase)
    private val updateInlineArrayUseCase = UpdateInlineArrayUseCase(repository, loadResourcesUseCase)
    private val toggleUntranslatableUseCase = ToggleUntranslatableUseCase(repository)


    private val mainPanel = JPanel(BorderLayout())
    private val tablePanel = ResourceTablePanel(scannerService)
    private val editPanel = ResourceEditPanel(project)

    private var pendingScrollToKey: String? = null
    private var currentFilter = "ALL"

    private var currentEditingOldKey: String? = null

    init {
        setupToolbar()

        mainPanel.add(tablePanel, BorderLayout.CENTER)
        mainPanel.add(editPanel, BorderLayout.SOUTH)

        wireUpCallbacks()
        reloadTableData()
    }

    private fun wireUpCallbacks() {
        tablePanel.onEditRequested = { key ->
            currentEditingOldKey = key
            val resource = loadResourcesUseCase().find { it.key == key }
            if (resource != null) editPanel.showForUpdate(resource)
        }

        tablePanel.onDeleteRequested = { key, type, isSubItem ->
            if (isSubItem) {
                val isPlural = !type.startsWith("item[")

                val proceed = if (isPlural) {
                    Messages.showYesNoDialog(
                        project,
                        KmpResourcesBundle.message("dialog.delete.plural.message", type, key),
                        KmpResourcesBundle.message("dialog.delete.plural.title"),
                        Messages.getQuestionIcon()
                    ) == Messages.YES
                } else {
                    true
                }

                if (proceed) {
                    deleteResourceUseCase(key, type, isSubItem)
                    reloadTableData()
                    triggerGradleSyncBackground()
                }
            } else {
                ReadAction.nonBlocking<Boolean> {
                    scannerService.isResourceUsed(key)
                }
                    .finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { isUsed ->
                        if (!isUsed) {
                            if (Messages.showYesNoDialog(
                                    project,
                                    KmpResourcesBundle.message("dialog.delete.resource.message", key),
                                    KmpResourcesBundle.message("dialog.delete.resource.title"),
                                    Messages.getQuestionIcon()
                                ) == Messages.YES
                            ) {
                                deleteResourceUseCase(key, type, isSubItem)
                                reloadTableData()
                                triggerGradleSyncBackground()
                            }
                        } else {
                            if (Messages.showDialog(
                                    project,
                                    KmpResourcesBundle.message("dialog.key.in.use.message", key),
                                    KmpResourcesBundle.message("dialog.key.in.use.title"),
                                    arrayOf(
                                        KmpResourcesBundle.message("dialog.button.show.usages"),
                                        KmpResourcesBundle.message("dialog.button.cancel")
                                    ),
                                    0,
                                    Messages.getWarningIcon()
                                ) == 0
                            ) {
                                triggerNativeFindUsages(key)
                            }
                        }
                    }
                    .submit(AppExecutorUtil.getAppExecutorService())
            }
        }

        tablePanel.onUsageRequested = { triggerNativeFindUsages(it) }

        tablePanel.onInlineStringEdited = { key, isUn, newValue ->
            updateInlineStringUseCase(key, isUn, newValue)
            triggerGradleSyncBackground()
        }

        tablePanel.onInlinePluralEdited = { key, isUn, quantity, newValue ->
            updateInlinePluralUseCase(key, isUn, quantity, newValue)
            triggerGradleSyncBackground()
        }

        tablePanel.onInlineArrayEdited = { key, isUn, index, newValue ->
            updateInlineArrayUseCase(key, isUn, index, newValue)
            ApplicationManager.getApplication().invokeLater { reloadTableData() }
            triggerGradleSyncBackground()
        }

        tablePanel.onUntranslatableToggled = { key, isUn -> toggleUntranslatableUseCase(key, isUn) }

        editPanel.onSaveRequested = { resourceToSave ->
            if (editPanel.isVisible && resourceToSave.key.isNotBlank()) {
                val existing = loadResourcesUseCase().find { it.key == resourceToSave.key }

                if (existing != null && existing.xmlTag != resourceToSave.xmlTag && currentEditingOldKey != resourceToSave.key) {
                    Messages.showErrorDialog(
                        project,
                        KmpResourcesBundle.message("dialog.error.key.exists", resourceToSave.key),
                        KmpResourcesBundle.message("dialog.error.title")
                    )
                } else {
                    val oldKey = currentEditingOldKey
                    val isRename = oldKey != null && oldKey != resourceToSave.key
                    val type = when (resourceToSave) {
                        is StringResource -> "string"
                        is PluralsResource -> "plurals"
                        is StringArrayResource -> "string-array"
                    }

                    if (isRename) {
                        KmpResourceRefactorService.renameKeyInModule(project, file, type, oldKey, resourceToSave.key)

                        WriteCommandAction.runWriteCommandAction(project, "Rename XML Key", "KMP Resources", {
                            val psiFile =
                                PsiManager.getInstance(project).findFile(file) as? XmlFile
                            val targetTag = psiFile?.rootTag?.subTags?.find {
                                it.name == resourceToSave.xmlTag && it.getAttributeValue("name") == oldKey
                            }
                            targetTag?.setAttribute("name", resourceToSave.key)
                        })
                    }

                    saveResourceUseCase(resourceToSave)

                    editPanel.isVisible = false
                    currentEditingOldKey = null
                    reloadTableData()
                    tablePanel.scrollToKey(resourceToSave.key)

                    triggerGradleSyncBackground()
                }
            }
        }
    }

    private fun setupToolbar() {
        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(object : AnAction(
            KmpResourcesBundle.message("action.add.key.text"),
            KmpResourcesBundle.message("action.add.key.description"),
            AllIcons.General.Add
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                currentEditingOldKey = null
                editPanel.showForAdd()
            }
        })
        actionGroup.addAction(object : AnAction(
            KmpResourcesBundle.message("action.remove.key.text"),
            KmpResourcesBundle.message("action.remove.key.description"),
            AllIcons.General.Remove
        ) {
            override fun actionPerformed(e: AnActionEvent) = tablePanel.triggerDeleteForSelectedRow()
            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = tablePanel.hasSelection()
            }

            override fun getActionUpdateThread() = ActionUpdateThread.EDT
        })

        actionGroup.addSeparator()

        actionGroup.addAction(object : AnAction(
            KmpResourcesBundle.message("action.sync.gradle.text"),
            KmpResourcesBundle.message("action.sync.gradle.desc"),
            AllIcons.Actions.Refresh
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                triggerGradleSyncBackground()
            }
        })

        actionGroup.addSeparator()

        val filterAction = object : ComboBoxAction() {
            override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
                val group = DefaultActionGroup()
                group.addAction(createFilterOption("ALL", "action.filter.keys.all"))
                group.addAction(createFilterOption("STRINGS", "action.filter.keys.strings"))
                group.addAction(createFilterOption("PLURALS", "action.filter.keys.plurals"))
                group.addAction(createFilterOption("ARRAYS", "action.filter.keys.arrays"))
                return group
            }

            override fun update(e: AnActionEvent) {
                super.update(e)
                e.presentation.text = when (currentFilter) {
                    "ALL" -> KmpResourcesBundle.message("action.filter.keys.all")
                    "STRINGS" -> KmpResourcesBundle.message("action.filter.keys.strings")
                    "PLURALS" -> KmpResourcesBundle.message("action.filter.keys.plurals")
                    "ARRAYS" -> KmpResourcesBundle.message("action.filter.keys.arrays")
                    else -> ""
                }
            }

            override fun getActionUpdateThread() = ActionUpdateThread.BGT
        }
        actionGroup.addAction(filterAction)

        val toolbar = ActionManager.getInstance().createActionToolbar("KmpResourceEditorToolbar", actionGroup, true)
        toolbar.targetComponent = mainPanel
        mainPanel.add(toolbar.component, BorderLayout.NORTH)
    }

    private fun createFilterOption(filter: String, bundleKey: String) =
        object : AnAction(KmpResourcesBundle.message(bundleKey)) {
            override fun actionPerformed(e: AnActionEvent) {
                currentFilter = filter; tablePanel.applyFilter(filter)
            }
        }

    private fun reloadTableData() {
        val resources = loadResourcesUseCase()
        tablePanel.updateData(resources)
    }

    private fun triggerNativeFindUsages(keyName: String) {
        val normalizedKey = keyName.replace(".", "_").replace("-", "_")

        val findModel = FindModel().apply {
            stringToFind = normalizedKey
            isCaseSensitive = true
            isProjectScope = false
            isCustomScope = true
            customScopeName = "KMP Usages"
        }

        findModel.customScope = KmpUsageSearchScope(GlobalSearchScope.projectScope(project))

        FindInProjectManager.getInstance(project).startFindInProject(findModel)
    }

    private fun triggerGradleSyncBackground() {
        ReadAction.nonBlocking<Unit> {
            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return@nonBlocking
            val basePath =
                ExternalSystemApiUtil.getExternalProjectPath(module) ?: project.basePath ?: return@nonBlocking
            val systemId = ProjectSystemId("GRADLE")

            val settings = ExternalSystemTaskExecutionSettings().apply {
                externalProjectPath = basePath
                taskNames = listOf("generateResourceAccessorsForCommonMain")
                externalSystemIdString = "GRADLE"
            }

            val callback = object : TaskCallback {
                override fun onSuccess() {
                    val generatedDir = LocalFileSystem.getInstance()
                        .findFileByPath("$basePath/build/generated")

                    if (generatedDir != null) {
                        ApplicationManager.getApplication().invokeLater {
                            VfsUtil.markDirtyAndRefresh(true, true, true, generatedDir)
                        }
                    }
                }

                override fun onFailure() {}
            }

            val spec = TaskExecutionSpec.create()
                .withProject(project)
                .withSystemId(systemId)
                .withExecutorId("Run")
                .withSettings(settings)
                .withProgressExecutionMode(ProgressExecutionMode.IN_BACKGROUND_ASYNC)
                .withCallback(callback)
                .build()

            ApplicationManager.getApplication().invokeLater {
                ExternalSystemUtil.runTask(spec)
            }
        }.submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun selectNotify() {
        ApplicationManager.getApplication().invokeLater {
            reloadTableData()

            pendingScrollToKey?.let {
                tablePanel.scrollToKey(it)
                pendingScrollToKey = null
            }
        }
    }

    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = mainPanel
    override fun getName(): String = KmpResourcesBundle.message("editor.tab.name")
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null

    fun scrollToKey(key: String) {
        pendingScrollToKey = key
        tablePanel.scrollToKey(key)
    }

    override fun dispose() {}
    override fun getFile(): VirtualFile = file
}

class KmpUsageSearchScope(baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(baseScope) {

    override fun contains(file: VirtualFile): Boolean {
        val path = file.path
        return !path.contains("/generated/") &&
                !path.contains("/build/") &&
                !path.endsWith(".cvr") &&
                super.contains(file)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KmpUsageSearchScope
        return myBaseScope == other.myBaseScope
    }

    override fun hashCode(): Int {
        return myBaseScope.hashCode() * 31 + javaClass.name.hashCode()
    }

    override fun toString() = "KMP Usages"
}