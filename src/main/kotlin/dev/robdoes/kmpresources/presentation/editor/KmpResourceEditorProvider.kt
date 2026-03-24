package dev.robdoes.kmpresources.presentation.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryImpl

class KmpResourceEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.name != "string.xml") return false
        return file.path.contains("/composeResources/")
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val factory = project.service<XmlResourceRepositoryFactory>()
        val repository = factory.create(file)
        return KmpResourceTableEditor(project = project, file = file, repository = repository)
    }

    override fun getEditorTypeId(): String = "KmpResourceTableEditor"

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
    }
}