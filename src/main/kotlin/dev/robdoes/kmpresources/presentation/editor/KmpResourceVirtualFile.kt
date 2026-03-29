package dev.robdoes.kmpresources.presentation.editor

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile

class KmpResourceVirtualFile(
    val modulePath: String,
    val defaultStringsFile: VirtualFile
) : LightVirtualFile("KMP Resources - $modulePath") {

    init {
        isWritable = false
    }

    override fun getFileType(): FileType {
        return KmpResourceFileType
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KmpResourceVirtualFile) return false
        return defaultStringsFile.path == other.defaultStringsFile.path
    }

    override fun hashCode(): Int {
        return defaultStringsFile.path.hashCode()
    }
}