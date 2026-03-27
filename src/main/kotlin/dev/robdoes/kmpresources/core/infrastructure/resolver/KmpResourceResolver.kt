package dev.robdoes.kmpresources.core.infrastructure.resolver

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import dev.robdoes.kmpresources.domain.model.ResourceType
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

object KmpResourceResolver {


    data class ResolvedResource(val key: String, val type: ResourceType) {
        val xmlTag: String get() = type.xmlTag
    }

    private val RESOURCE_CACHE_KEY =
        Key.create<CachedValue<Map<ResolvedResource, List<SmartPsiElementPointer<XmlTag>>>>>("KmpResourceCache")

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

        val type = ResourceType.fromKotlinAccessor(text) ?: return null

        val prefix = "${type.kotlinAccessor}."
        val key = text.substringAfter(prefix)

        if (key.isNotBlank()) {
            return ResolvedResource(key, type)
        }

        return null
    }

    fun findXmlTags(project: Project, resolved: ResolvedResource): List<XmlTag> {
        return try {
            val cachedMap = CachedValuesManager.getManager(project).getCachedValue(project, RESOURCE_CACHE_KEY, {
                val map = mutableMapOf<ResolvedResource, MutableList<SmartPsiElementPointer<XmlTag>>>()
                val pointerManager = SmartPointerManager.getInstance(project)

                val scope = GlobalSearchScope.projectScope(project)
                val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope)

                for (vFile in xmlFiles) {
                    if (!vFile.path.contains("/composeResources/")) continue

                    val xmlFile = PsiManager.getInstance(project).findFile(vFile) as? XmlFile ?: continue
                    val rootTag = xmlFile.rootTag ?: continue

                    for (tag in rootTag.subTags) {
                        val rawName = tag.getAttributeValue("name") ?: continue
                        val normalizedName = ResourceKeyNormalizer.normalize(rawName)
                        val resType = ResourceType.fromXmlTag(tag.name) ?: continue

                        val resKey = ResolvedResource(normalizedName, resType)

                        map.getOrPut(resKey) { mutableListOf() }
                            .add(pointerManager.createSmartPsiElementPointer(tag))
                    }
                }

                val resultMap: Map<ResolvedResource, List<SmartPsiElementPointer<XmlTag>>> = map
                CachedValueProvider.Result.create(resultMap, PsiModificationTracker.MODIFICATION_COUNT)
            }, false)

            cachedMap[resolved]?.mapNotNull { it.element } ?: emptyList()
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            if (e.javaClass.name.contains("JobCancellationException")) {
                throw ProcessCanceledException(e)
            }
            emptyList()
        }
    }
}