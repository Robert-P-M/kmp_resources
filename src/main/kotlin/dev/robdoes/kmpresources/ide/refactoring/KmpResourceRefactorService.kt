package dev.robdoes.kmpresources.ide.refactoring

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object KmpResourceRefactorService {

    suspend fun renameKeyInModule(
        project: Project,
        xmlFile: VirtualFile,
        resourceType: String,
        oldKey: String,
        newKey: String
    ) {
        if (oldKey == newKey) return

        val affectedFilePointers = findAffectedFiles(project, xmlFile, oldKey) ?: return

        withEdtContext {
            WriteCommandAction.runWriteCommandAction(project, "Rename KMP Resource Key", "KMP Resources", {
                val psiFactory = KtPsiFactory(project)
                val documentManager = PsiDocumentManager.getInstance(project)

                documentManager.commitAllDocuments()

                XmlResourceUpdater.updateKey(project, xmlFile, resourceType, oldKey, newKey)

                for (pointer in affectedFilePointers) {
                    val ktFile = pointer.element ?: continue

                    KotlinUsageUpdater.updateUsages(ktFile, psiFactory, resourceType, oldKey, newKey)
                    KotlinImportUpdater.updateImports(ktFile, psiFactory, oldKey, newKey)
                }

                documentManager.commitAllDocuments()
            })
        }
    }

    private suspend fun findAffectedFiles(
        project: Project,
        xmlFile: VirtualFile,
        oldKey: String
    ): List<SmartPsiElementPointer<KtFile>>? {
        val module = readAction { ModuleUtilCore.findModuleForFile(xmlFile, project) } ?: return null

        return readAction {
            val moduleScope = GlobalSearchScope.moduleScope(module)
            val normalizedOldKey = ResourceKeyNormalizer.normalize(oldKey)
            val pointers = mutableListOf<SmartPsiElementPointer<KtFile>>()
            val psiManager = PsiManager.getInstance(project)
            val pointerManager = SmartPointerManager.getInstance(project)

            PsiSearchHelper.getInstance(project).processAllFilesWithWord(
                normalizedOldKey,
                moduleScope,
                { psiFile ->
                    if (psiFile.fileType == KotlinFileType.INSTANCE) {
                        val ktFile = psiManager.findFile(psiFile.virtualFile) as? KtFile
                        if (ktFile != null) {
                            pointers.add(pointerManager.createSmartPsiElementPointer(ktFile))
                        }
                    }
                    true
                },
                true
            )
            pointers
        }
    }
}


private object XmlResourceUpdater {
    fun updateKey(project: Project, xmlFile: VirtualFile, resourceType: String, oldKey: String, newKey: String) {
        val psiXmlFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile
        val targetTag = psiXmlFile?.rootTag?.subTags?.find {
            it.name == resourceType && it.getAttributeValue("name") == oldKey
        }
        targetTag?.setAttribute("name", newKey)
    }
}

private object KotlinUsageUpdater {
    fun updateUsages(ktFile: KtFile, psiFactory: KtPsiFactory, resourceType: String, oldKey: String, newKey: String) {
        val ktType = if (resourceType == "string-array") "array" else resourceType
        val fullOldReference = "Res.$ktType.$oldKey"

        val isImported = ktFile.importDirectives.any { it.importedName?.asString() == oldKey }

        val references = ktFile.collectDescendantsOfType<KtNameReferenceExpression> {
            if (it.text != oldKey) return@collectDescendantsOfType false
            if (it.parent is KtImportDirective) return@collectDescendantsOfType false
            it.parent.text == fullOldReference || isImported
        }

        for (ref in references) {
            ref.replace(psiFactory.createNameIdentifier(newKey))
        }
    }
}

private object KotlinImportUpdater {
    fun updateImports(ktFile: KtFile, psiFactory: KtPsiFactory, oldKey: String, newKey: String) {
        val imports = ktFile.importDirectives.filter { it.importedName?.asString() == oldKey }
        for (import in imports) {
            val importedRef = import.importedReference ?: continue
            val nameExpressions = importedRef.collectDescendantsOfType<KtNameReferenceExpression> { it.text == oldKey }
            nameExpressions.lastOrNull()?.replace(psiFactory.createNameIdentifier(newKey))
        }
    }
}