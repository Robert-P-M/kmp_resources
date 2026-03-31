package dev.robdoes.kmpresources.presentation.editor.search

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle

/**
 * A custom implementation of [DelegatingGlobalSearchScope] that adjusts the search scope
 * to exclude files generated during build processes or residing in build directories.
 * Specifically, it limits the search to Kotlin (`.kt`) and XML (`.xml`) files.
 *
 * This scope ensures that the path of a file does not contain certain excluded patterns,
 * thereby refining the set of files considered suitable for search operations.
 *
 * @param baseScope The base [GlobalSearchScope] to delegate to.
 */
internal class KmpUsageSearchScope(baseScope: GlobalSearchScope) : DelegatingGlobalSearchScope(baseScope) {

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