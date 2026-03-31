package dev.robdoes.kmpresources.presentation.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import javax.swing.Icon

/**
 * Represents a custom file type for KMP resource files used within the application.
 *
 * This file type is tailored for editing and managing resource tables specific to the
 * Kotlin Multiplatform project (KMP), with support for handling multi-locale translation
 * resources. It is integrated into the IntelliJ platform and provides a dedicated way
 * to represent and manage these resource files within the IDE.
 *
 * Key characteristics of this file type include:
 * - Custom name and description for better identification within the IDE.
 * - Ability to manage structured data tables with resource information.
 * - Marked as a binary file type.
 * - Supports both read and write operations.
 * - Does not specify a default file extension.
 * - Returns `null` for character set definitions as it does not apply to this type.
 *
 * The associated icon provides a visual representation of the file type in the IDE.
 */
internal object KmpResourceFileType : FileType {
    override fun getName(): String = "KMP Resource View"

    override fun getDescription(): String = KmpResourcesBundle.message("filetype.kmp.resource.description")

    override fun getDefaultExtension(): String = ""

    override fun getIcon(): Icon = AllIcons.Nodes.DataTables

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = false

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}