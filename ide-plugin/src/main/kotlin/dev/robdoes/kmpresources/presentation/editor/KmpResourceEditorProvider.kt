package dev.robdoes.kmpresources.presentation.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory

/**
 * Provides a custom editor for KMP resource files.
 *
 * This class implements the `FileEditorProvider` interface and is responsible for
 * registering a custom editor for `KmpResourceVirtualFile` instances. The provider
 * supports opening, viewing, and editing KMP resource files in a specialized table editor.
 */
internal class KmpResourceEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is KmpResourceVirtualFile
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val kmpVirtualFile = file as KmpResourceVirtualFile
        val factory = project.service<XmlResourceRepositoryFactory>()

        val repository = factory.create(kmpVirtualFile.defaultStringsFile)

        return KmpResourceTableEditor(
            project = project,
            file = kmpVirtualFile.defaultStringsFile,
            repository = repository
        )
    }

    override fun getEditorTypeId(): String = "KmpResourceTableEditor"

    override fun getPolicy(): FileEditorPolicy {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR
    }
}