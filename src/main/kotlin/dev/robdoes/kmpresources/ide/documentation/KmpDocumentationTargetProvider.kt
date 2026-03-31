package dev.robdoes.kmpresources.ide.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver

/**
 * Provides documentation target resolution for Kotlin Multiplatform Project (KMP) resources.
 *
 * This class implements the `PsiDocumentationTargetProvider` interface and is responsible
 * for generating a `DocumentationTarget` based on the resolved resource references in
 * Kotlin code and the associated XML tags in the project.
 *
 * The process involves:
 * - Resolving the original or provided `PsiElement` into a resource key and type.
 * - Searching for XML tags in the project's resource files that match the resolved resource.
 * - Selecting the most appropriate XML tag to represent the resource documentation.
 *
 * If no matching resources or XML tags are found, `null` is returned.
 */
internal class KmpDocumentationTargetProvider : PsiDocumentationTargetProvider {

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