package dev.robdoes.kmpresources.core.util

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector
import kotlin.collections.iterator

object KmpResourceResolver {

    private val RESOURCE_PREFIXES = mapOf(
        "Res.string." to "string",
        "Res.plurals." to "plurals",
        "Res.array." to "string-array"
    )

    data class ResolvedResource(val key: String, val xmlTag: String)

    fun resolveReference(element: PsiElement): ResolvedResource? {
        var dotQualified: KtDotQualifiedExpression? = null

        if (element is KtNameReferenceExpression) {
            dotQualified = element.getQualifiedExpressionForSelector() as? KtDotQualifiedExpression
        }

        if (dotQualified == null) {
            dotQualified = generateSequence(element) { it.parent }
                .filterIsInstance<KtDotQualifiedExpression>()
                .firstOrNull()
        }

        val text = dotQualified?.text ?: return null

        for ((prefix, tag) in RESOURCE_PREFIXES) {
            if (text.startsWith(prefix)) {
                val key = text.substringAfter(prefix)
                if (key.isNotBlank()) return ResolvedResource(key, tag)
            }
        }
        return null
    }

    fun findXmlTags(project: Project, resolved: ResolvedResource): List<XmlTag> {
        val scope = GlobalSearchScope.projectScope(project)
        val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)

        return xmlFiles.asSequence()
            .filter { it.path.contains("/composeResources/") }
            .mapNotNull { PsiManager.getInstance(project).findFile(it) as? XmlFile }
            .mapNotNull { it.rootTag }
            .flatMap { it.findSubTags(resolved.xmlTag).asSequence() }
            .filter { tag ->
                val rawXmlName = tag.getAttributeValue("name") ?: ""
                val normalizedName = rawXmlName.replace(".", "_").replace("-", "_")
                normalizedName == resolved.key
            }
            .toList()
    }
}