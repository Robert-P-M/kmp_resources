package dev.robdoes.kmpresources.ide.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver

/**
 * Handles "Go To Declaration" functionality for Kotlin Multiplatform resource references.
 *
 * This handler allows navigation from Kotlin code elements referencing resources to their corresponding
 * XML definitions in the project. It identifies the target XML tags by resolving resource keys and locating
 * matching definitions in resource files.
 */
internal class KmpGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null) return null
        val project = sourceElement.project

        val resolved = KmpResourceResolver.resolveReference(sourceElement) ?: return null
        val tags = KmpResourceResolver.findXmlTags(project, resolved)

        if (tags.isEmpty()) return null

        val defaultTag = tags.find { tag ->
            val dirName = tag.containingFile?.virtualFile?.parent?.name
            dirName == "values"
        } ?: tags.first()

        return arrayOf(KmpResourceTarget(defaultTag, resolved.key))
    }

    override fun getActionText(context: DataContext): String =
        KmpResourcesBundle.message("action.goto.kmp.resource.text")
}