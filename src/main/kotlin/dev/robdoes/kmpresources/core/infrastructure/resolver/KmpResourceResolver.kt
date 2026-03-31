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

/**
 * The `KmpResourceResolver` object provides functionality for resolving and managing resource references
 * within a Kotlin Multiplatform Project, specifically targeting XML resources used by Compose UI.
 * It handles resource resolution and lookup based on Kotlin code references or XML declarations
 * while leveraging a caching mechanism to optimize performance.
 */
internal object KmpResourceResolver {


    /**
     * Represents a resolved resource with its key and type.
     *
     * This class is used to define the key and the type of a resource
     * that is resolved from a source reference, such as a Kotlin file or an XML resource.
     *
     * @property key The unique identifier of the resource.
     * @property type The type of the resource, defined as a `ResourceType`.
     * @property xmlTag A derived property providing the XML tag associated with the resource type.
     */
    data class ResolvedResource(val key: String, val type: ResourceType) {
        val xmlTag: String get() = type.xmlTag
    }

    /**
     * A unique key used for caching resolved resources and their associated XML tags within a project's scope.
     *
     * This key is utilized to efficiently retrieve a cached map that associates `ResolvedResource` instances
     * with lists of `SmartPsiElementPointer<XmlTag>`, representing the XML tags corresponding to the resources.
     *
     * The caching mechanism leverages IntelliJ's `CachedValuesManager` to reduce repeated computations and ensure
     * accurate and up-to-date data based on the modification state of the project (`PsiModificationTracker.MODIFICATION_COUNT`).
     *
     */
    private val RESOURCE_CACHE_KEY =
        Key.create<CachedValue<Map<ResolvedResource, List<SmartPsiElementPointer<XmlTag>>>>>("KmpResourceCache")

    /**
     * Resolves the given PsiElement into a corresponding resource key and type.
     *
     * This method analyzes a Kotlin code element to determine if it represents
     * a reference to a resource (e.g., a string, array, or plural resource).
     * If the reference is valid, it extracts the key and identifies the type of resource.
     *
     * @param element The PsiElement to resolve. Typically represents an element in Kotlin code
     *                that may refer to a resource, such as a `KtNameReferenceExpression`.
     * @return A `ResolvedResource` containing the resource key and type if the resolution is successful,
     *         or `null` if the element does not represent a valid resource reference.
     */
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

    /**
     * Searches for XML tags in the project's resources that match the specified resolved resource.
     *
     * This method scans the project's XML resource files to find tags that align with the provided
     * `ResolvedResource` object. The search scope is limited to project resources tied to Compose,
     * and the results are cached for performance optimization.
     *
     * @param project The current project within which the search is performed.
     * @param resolved The resolved resource object containing the key and type used to locate matching XML tags.
     * @return A list of matching XML tags as `XmlTag` objects. If no matches are found, an empty list is returned.
     */
    fun findXmlTags(project: Project, resolved: ResolvedResource): List<XmlTag> {
        return try {
            val cachedMap = CachedValuesManager.getManager(project).getCachedValue(project, RESOURCE_CACHE_KEY, {
                val map = mutableMapOf<ResolvedResource, MutableList<SmartPsiElementPointer<XmlTag>>>()
                val pointerManager = SmartPointerManager.getInstance(project)

                val composeScope = object : GlobalSearchScope(project) {
                    override fun contains(file: com.intellij.openapi.vfs.VirtualFile): Boolean {
                        return file.path.contains("/composeResources/")
                    }

                    override fun isSearchInModuleContent(aModule: com.intellij.openapi.module.Module) = true
                    override fun isSearchInLibraries() = false
                }
                val searchScope = GlobalSearchScope.projectScope(project).intersectWith(composeScope)

                val xmlFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, searchScope)

                for (vFile in xmlFiles) {
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