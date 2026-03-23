package dev.robdoes.kmpresources.refactoring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object KmpResourceRefactorService {

    fun renameKeyInModule(
        project: Project,
        xmlFile: VirtualFile,
        resourceType: String,
        oldKey: String,
        newKey: String
    ) {
        if (oldKey == newKey) return

        val module = ModuleUtilCore.findModuleForFile(xmlFile, project) ?: return
        val scope = GlobalSearchScope.moduleScope(module)

        val kotlinFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)
        val psiManager = PsiManager.getInstance(project)
        val psiFactory = KtPsiFactory(project)

        val ktType = if (resourceType == "string-array") "array" else resourceType
        val fullOldReference = "Res.$ktType.$oldKey"

        WriteCommandAction.runWriteCommandAction(project, "Rename KMP Resource Key", "KMP Resources", {
            for (vFile in kotlinFiles) {
                val ktFile = psiManager.findFile(vFile) as? KtFile ?: continue

                val references = ktFile.collectDescendantsOfType<KtNameReferenceExpression> {
                    it.text == oldKey && it.parent.text == fullOldReference
                }

                for (ref in references) {
                    ref.replace(psiFactory.createNameIdentifier(newKey))
                }

                val imports = ktFile.importDirectives.filter { it.importedName?.asString() == oldKey }
                for (import in imports) {
                    val importedRef = import.importedReference ?: continue
                    val nameExpressions = importedRef.collectDescendantsOfType<KtNameReferenceExpression> { it.text == oldKey }
                    nameExpressions.lastOrNull()?.replace(psiFactory.createNameIdentifier(newKey))
                }
            }
        })
    }
}