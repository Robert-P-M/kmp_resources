package dev.robdoes.kmpresources.ide.linter

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.ResourceType

/**
 * Inspects XML resource files for duplicate resource keys within `composeResources/values` directories.
 *
 * This inspection helps to identify and prevent errors caused by duplicate resource keys, which can
 * lead to undefined behavior or conflicts when resolving resources in a project. Duplicate keys
 * are flagged with an error in the `Problems` view and can optionally be fixed by removing the
 * duplicate resource.
 *
 * Functionality:
 * - Processes only XML files under directories with a structure containing `composeResources/values`.
 * - Locates duplicate `name` attributes among valid resource XML tags (e.g., `string`, `color`).
 * - Registers a problem for every duplicate key found and provides a quick-fix option to remove it.
 */
internal class KmpDuplicateResourceKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : XmlElementVisitor() {
            override fun visitXmlTag(tag: XmlTag) {
                if (tag.name != "resources") return

                val containingFile = tag.containingFile ?: return
                val vFile = containingFile.virtualFile ?: return

                if (vFile.extension != "xml") return
                if (!vFile.path.contains("composeResources") || !vFile.parent.name.startsWith("values")) return

                val seenKeys = mutableSetOf<String>()

                for (subTag in tag.subTags) {
                    if (ResourceType.fromXmlTag(subTag.name) == null) continue
                    val keyName = subTag.getAttributeValue("name") ?: continue
                    if (keyName.isBlank()) continue

                    if (!seenKeys.add(keyName)) {
                        val errorElement = subTag.getAttribute("name") ?: subTag

                        holder.registerProblem(
                            errorElement,
                            KmpResourcesBundle.message("inspection.duplicate.key.message", keyName),
                            ProblemHighlightType.ERROR,
                            RemoveDuplicateResourceFix(keyName)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A quick-fix implementation for resolving duplicate resource keys in XML files.
 * This fix is triggered by `KmpDuplicateResourceKeyInspection` when a duplicate
 * resource key is detected within the `values` directory of a Compose Resources file.
 *
 * The fix removes the conflicting XML tag associated with the duplicate resource
 * key to resolve the issue.
 *
 * @constructor Initializes the quick-fix with the name of the duplicate resource key.
 * @param keyName The name of the duplicate resource key detected.
 */
internal class RemoveDuplicateResourceFix(private val keyName: String) : LocalQuickFix {
    override fun getFamilyName(): String = KmpResourcesBundle.message("inspection.duplicate.key.fix.family")

    override fun getName(): String = KmpResourcesBundle.message("inspection.duplicate.key.fix.name", keyName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        val tag = (element as? XmlAttribute)?.parent ?: element as? XmlTag ?: return
        tag.delete()
    }
}