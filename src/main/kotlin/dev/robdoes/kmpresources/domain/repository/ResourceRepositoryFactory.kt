package dev.robdoes.kmpresources.domain.repository

import com.intellij.openapi.vfs.VirtualFile

interface ResourceRepositoryFactory {
    fun create(file: VirtualFile): ResourceRepository
}