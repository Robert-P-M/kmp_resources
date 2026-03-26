package dev.robdoes.kmpresources.ide.linter

import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.core.KmpResourcesBundle

class KmpDuplicateResourceKeyInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : XmlElementVisitor() {
            override fun visitXmlTag(tag: XmlTag) {
                if (tag.name != "resources") return

                val containingFile = tag.containingFile ?: return
                if (containingFile.name != "strings.xml" && containingFile.name != "string.xml") return
                if (!containingFile.virtualFile.path.contains("composeResources")) return

                val seenKeys = mutableSetOf<String>()

                for (subTag in tag.subTags) {
                    val resourceType = subTag.name
                    if (resourceType != "string" && resourceType != "plurals" && resourceType != "string-array") continue

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

class RemoveDuplicateResourceFix(private val keyName: String) : LocalQuickFix {
    override fun getFamilyName(): String = KmpResourcesBundle.message("inspection.duplicate.key.fix.family")

    override fun getName(): String = KmpResourcesBundle.message("inspection.duplicate.key.fix.name", keyName)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        val tag = (element as? XmlAttribute)?.parent ?: element as? XmlTag ?: return
        tag.delete()
    }
}