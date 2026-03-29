package dev.robdoes.kmpresources.ide.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
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
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver
import dev.robdoes.kmpresources.data.repository.XmlResourceWriter
import dev.robdoes.kmpresources.domain.model.PluralsResource
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.domain.model.StringArrayResource
import dev.robdoes.kmpresources.domain.model.StringResource
import dev.robdoes.kmpresources.ide.utils.KmpGradleSyncHelper
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import javax.swing.Icon

class KmpCreateResourceIntention : PsiElementBaseIntentionAction(), PriorityAction, Iconable {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getIcon(flags: Int): Icon = AllIcons.General.Error

    override fun getFamilyName(): String = KmpResourcesBundle.message("intention.create.resource.family")

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val resolved = KmpResourceResolver.resolveReference(element) ?: return false
        text = KmpResourcesBundle.message("intention.create.resource.text", resolved.xmlTag, resolved.key)
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val resolved = KmpResourceResolver.resolveReference(element) ?: return

        val value = if (ApplicationManager.getApplication().isUnitTestMode) {
            "Test Value"
        } else {
            Messages.showInputDialog(
                project,
                KmpResourcesBundle.message("intention.create.resource.prompt", resolved.key),
                KmpResourcesBundle.message("intention.create.resource.title"),
                Messages.getQuestionIcon()
            )
        }
        if (value.isNullOrBlank()) return

        val stringsFile = runReadAction {
            val scope = GlobalSearchScope.projectScope(project)

            val composeResFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope).filter {
                it.extension == "xml" && it.parent?.name == "values" && it.path.contains("composeResources")
            }.filter {
                val xml = PsiManager.getInstance(project).findFile(it) as? XmlFile
                xml?.rootTag?.name == "resources"
            }

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

        val resourceToSave = when (resolved.type) {
            ResourceType.String -> StringResource(resolved.key, false, mapOf(null to value))
            ResourceType.Plural -> PluralsResource(resolved.key, false, mapOf(null to mapOf("other" to value)))
            ResourceType.Array -> StringArrayResource(resolved.key, false, mapOf(null to listOf(value)))
        }

        WriteCommandAction.runWriteCommandAction(project, commandName, commandGroup, {
            val xmlFile =
                PsiManager.getInstance(project).findFile(stringsFile) as? XmlFile ?: return@runWriteCommandAction
            val resourcesTag = xmlFile.rootTag ?: return@runWriteCommandAction
            val factory = XmlElementFactory.getInstance(project)

            val newTag = XmlResourceWriter.createResourceTag(factory, resourceToSave, null)
            val addedTag = resourcesTag.add(newTag)

            CodeStyleManager.getInstance(project).reformat(addedTag)


            addKotlinImport(project, ktFilePointer?.element, basePackage, resolved)
        })

        PsiDocumentManager.getInstance(project).commitAllDocuments()
        FileDocumentManager.getInstance().saveAllDocuments()

        if (!ApplicationManager.getApplication().isUnitTestMode) {
            KmpGradleSyncHelper.triggerGenerateAccessors(project, stringsFile) {}
        }
    }

    private fun getBaseResourcePackage(ktFile: KtFile): String? {
        return ktFile.importDirectives
            .mapNotNull { it.importedFqName?.asString() }
            .find { it.endsWith(".Res") || it.contains(".generated.resources.") }
            ?.substringBeforeLast(".")
    }

    private fun addKotlinImport(
        project: Project,
        ktFile: KtFile?,
        basePackage: String?,
        resolved: KmpResourceResolver.ResolvedResource
    ) {
        if (ktFile == null || basePackage == null) return
        val importList = ktFile.importList ?: return

        val targetFqName = "$basePackage.${resolved.key}"

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
}