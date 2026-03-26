package dev.robdoes.kmpresources.ide.refactoring

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
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

        val module = readAction { ModuleUtilCore.findModuleForFile(xmlFile, project) } ?: return

        val affectedFiles = readAction {
            val moduleScope = GlobalSearchScope.moduleScope(module)
            val normalizedOldKey = oldKey.replace(".", "_").replace("-", "_")
            val files = mutableSetOf<VirtualFile>()

            PsiSearchHelper.getInstance(project).processAllFilesWithWord(
                normalizedOldKey,
                moduleScope,
                { psiFile ->
                    if (psiFile.fileType == KotlinFileType.INSTANCE) {
                        files.add(psiFile.virtualFile)
                    }
                    true
                },
                true
            )
            files
        }

        val writeActionTask = Runnable {
            WriteCommandAction.runWriteCommandAction(project, "Rename KMP Resource Key", "KMP Resources", {
                val psiManager = PsiManager.getInstance(project)
                val psiFactory = KtPsiFactory(project)
                val ktType = if (resourceType == "string-array") "array" else resourceType
                val fullOldReference = "Res.$ktType.$oldKey"

                val documentManager = PsiDocumentManager.getInstance(project)
                documentManager.commitAllDocuments()

                val psiXmlFile = psiManager.findFile(xmlFile) as? XmlFile
                val targetTag = psiXmlFile?.rootTag?.subTags?.find {
                    it.name == resourceType && it.getAttributeValue("name") == oldKey
                }
                targetTag?.setAttribute("name", newKey)

                for (vFile in affectedFiles) {
                    val ktFile = psiManager.findFile(vFile) as? KtFile ?: continue
                    val isImported = ktFile.importDirectives.any { it.importedName?.asString() == oldKey }

                    val references = ktFile.collectDescendantsOfType<KtNameReferenceExpression> {
                        if (it.text != oldKey) return@collectDescendantsOfType false
                        if (it.parent is org.jetbrains.kotlin.psi.KtImportDirective) return@collectDescendantsOfType false
                        it.parent.text == fullOldReference || isImported
                    }

                    for (ref in references) {
                        ref.replace(psiFactory.createNameIdentifier(newKey))
                    }

                    val imports = ktFile.importDirectives.filter { it.importedName?.asString() == oldKey }
                    for (import in imports) {
                        val importedRef = import.importedReference ?: continue
                        val nameExpressions =
                            importedRef.collectDescendantsOfType<KtNameReferenceExpression> { it.text == oldKey }
                        nameExpressions.lastOrNull()?.replace(psiFactory.createNameIdentifier(newKey))
                    }
                }

                documentManager.commitAllDocuments()
            })
        }

        if (ApplicationManager.getApplication().isDispatchThread) {
            writeActionTask.run()
        } else {
            withContext(Dispatchers.EDT) {
                writeActionTask.run()
            }
        }
    }
}