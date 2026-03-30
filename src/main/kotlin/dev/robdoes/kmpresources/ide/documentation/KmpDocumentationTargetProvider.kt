package dev.robdoes.kmpresources.ide.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver

class KmpDocumentationTargetProvider : PsiDocumentationTargetProvider {

    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        val resolveTarget = originalElement ?: element
        val resolved = KmpResourceResolver.resolveReference(resolveTarget) ?: return null
        val tags = KmpResourceResolver.findXmlTags(element.project, resolved)

        if (tags.isEmpty()) return null

        val defaultTag = tags.find { tag ->
            tag.containingFile.virtualFile?.parent?.name == "values"
        } ?: tags.first()

        return KmpDocumentationTarget(defaultTag, resolved.key)
    }
}