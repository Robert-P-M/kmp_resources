package dev.robdoes.kmpresources.ide.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Iconable
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import javax.swing.Icon

class KmpCreateResourceIntention : PsiElementBaseIntentionAction(), PriorityAction, Iconable {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getIcon(flags: Int): Icon = AllIcons.General.Error

    override fun getFamilyName(): String = KmpResourcesBundle.message("intention.create.resource.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val (type, key) = extractResourceInfo(element) ?: return false
        text = KmpResourcesBundle.message("intention.create.resource.text", type, key)
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val (resourceType, keyName) = extractResourceInfo(element) ?: return

        val value = if (ApplicationManager.getApplication().isUnitTestMode) {
            "Test Value"
        } else {
            Messages.showInputDialog(
                project,
                KmpResourcesBundle.message("intention.create.resource.prompt", keyName),
                KmpResourcesBundle.message("intention.create.resource.title"),
                Messages.getQuestionIcon()
            )
        }
        if (value.isNullOrBlank()) return

        val stringsFile = runReadAction {
            val scope = GlobalSearchScope.projectScope(project)
            val allFiles = FilenameIndex.getVirtualFilesByName("strings.xml", scope) +
                    FilenameIndex.getVirtualFilesByName("string.xml", scope)

            val composeResFiles = allFiles.filter { it.path.contains("composeResources") }

            val currentVFile = element.containingFile?.originalFile?.virtualFile
            if (currentVFile != null) {
                val fileIndex = ProjectRootManager.getInstance(project).fileIndex
                val contentRoot = fileIndex.getContentRootForFile(currentVFile)

                composeResFiles.firstOrNull { fileIndex.getContentRootForFile(it) == contentRoot }
                    ?: composeResFiles.firstOrNull()
            } else {
                composeResFiles.firstOrNull()
            }
        }

        if (stringsFile == null) {
            Messages.showErrorDialog(
                project,
                KmpResourcesBundle.message("dialog.error.missing.strings_xml"),
                KmpResourcesBundle.message("dialog.error.title")
            )
            return
        }

        val ktFile = element.containingFile as? KtFile
        val ktFilePointer = ktFile?.let { SmartPointerManager.getInstance(project).createSmartPsiElementPointer(it) }
        val basePackage = ktFile?.let { getBaseResourcePackage(it) }

        val commandName = KmpResourcesBundle.message("command.create.resource.name")
        val commandGroup = KmpResourcesBundle.message("ui.toolwindow.pane.title")

        WriteCommandAction.runWriteCommandAction(project, commandName, commandGroup, {
            val xmlFile =
                PsiManager.getInstance(project).findFile(stringsFile) as? XmlFile ?: return@runWriteCommandAction
            val resourcesTag = xmlFile.rootTag ?: return@runWriteCommandAction
            val factory = XmlElementFactory.getInstance(project)

            val tagText = when (resourceType) {
                "string" -> """<string name="$keyName">$value</string>"""
                "plurals" -> """<plurals name="$keyName"><item quantity="other">$value</item></plurals>"""
                "array" -> """<string-array name="$keyName"><item>$value</item></string-array>"""
                else -> return@runWriteCommandAction
            }

            val newTag = factory.createTagFromText(tagText)
            val addedTag = resourcesTag.add(newTag)
            CodeStyleManager.getInstance(project).reformat(addedTag)

            val documentManager = PsiDocumentManager.getInstance(project)
            val document = documentManager.getDocument(xmlFile)
            if (document != null) {
                documentManager.commitDocument(document)
            }
        })

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        if (ApplicationManager.getApplication().isUnitTestMode) {
            addKotlinImport(project, ktFilePointer?.element, basePackage, keyName)
        } else {
            KmpGradleSyncHelper.triggerGenerateAccessors(project, stringsFile) {
                ApplicationManager.getApplication().invokeLater {
                    WriteCommandAction.runWriteCommandAction(project, "Add KMP Import", commandGroup, {
                        addKotlinImport(project, ktFilePointer?.element, basePackage, keyName)
                    })
                }
            }
        }
    }

    private fun getBaseResourcePackage(ktFile: KtFile): String? {
        return ktFile.importDirectives
            .mapNotNull { it.importedFqName?.asString() }
            .find { it.endsWith(".Res") || it.contains(".generated.resources.") }
            ?.substringBeforeLast(".")
    }

    private fun addKotlinImport(project: Project, ktFile: KtFile?, basePackage: String?, keyName: String) {
        if (ktFile == null || basePackage == null) return
        val importList = ktFile.importList ?: return

        val targetFqName = "$basePackage.$keyName"

        val alreadyImported = ktFile.importDirectives.any {
            it.importedFqName?.asString() == targetFqName ||
                    (it.isAllUnder && it.importedFqName?.asString() == basePackage)
        }

        if (!alreadyImported) {
            val ktFactory = KtPsiFactory(project)
            val importDirective = ktFactory.createFile("import $targetFqName").importDirectives.first()

            importList.add(ktFactory.createWhiteSpace("\n"))
            importList.add(importDirective)
        }
    }

    private fun extractResourceInfo(element: PsiElement): Pair<String, String>? {
        val refExpr = element as? KtNameReferenceExpression
            ?: element.parent as? KtNameReferenceExpression
            ?: element.prevSibling as? KtNameReferenceExpression
            ?: element.prevSibling?.parent as? KtNameReferenceExpression
            ?: return null

        val dotExpr = refExpr.parent as? KtDotQualifiedExpression ?: return null

        if (dotExpr.selectorExpression != refExpr) return null

        val receiverText = dotExpr.receiverExpression.text

        val type = when {
            receiverText.endsWith("Res.string") -> "string"
            receiverText.endsWith("Res.plurals") -> "plurals"
            receiverText.endsWith("Res.array") -> "array"
            else -> return null
        }

        val key = refExpr.text
        if (key.isBlank()) return null

        return Pair(type, key)
    }
}