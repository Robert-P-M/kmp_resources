package dev.robdoes.kmpresources.presentation.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

object KmpResourceFileType : FileType {
    override fun getName(): String = "KMP Resource View"

    override fun getDescription(): String = "KMP Resources Table Editor"

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = AllIcons.Nodes.DataTables

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: com.intellij.openapi.vfs.VirtualFile, content: ByteArray): String? = null
}