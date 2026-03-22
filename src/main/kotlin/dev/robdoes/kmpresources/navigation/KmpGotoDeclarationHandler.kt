package dev.robdoes.kmpresources.navigation

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import dev.robdoes.kmpresources.KmpResourcesBundle
import dev.robdoes.kmpresources.util.KmpResourceResolver

class KmpGotoDeclarationHandler : GotoDeclarationHandler {

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

        return tags.map { KmpResourceTarget(it, resolved.key) }.toTypedArray()
    }

    override fun getActionText(context: DataContext): String =
        KmpResourcesBundle.message("action.goto.kmp.resource.text")
}