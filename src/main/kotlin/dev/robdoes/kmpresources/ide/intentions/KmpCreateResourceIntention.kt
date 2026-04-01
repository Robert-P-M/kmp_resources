package dev.robdoes.kmpresources.ide.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.icons.AllIcons
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import dev.robdoes.kmpresources.core.application.service.ResourceSystemDetectionService
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

/**
 * Provides an intention to create a missing resource (e.g., string, plural, or array)
 * within the appropriate XML resource file in a cross-platform Kotlin Multiplatform Project (KMP) context.
 *
 * This class extends `PsiElementBaseIntentionAction` and is specifically designed to help developers
 * resolve missing resources referenced in code by creating them dynamically in the corresponding
 * XML resource file. The intention is shown when the caret is on a reference to a non-existent resource.
 *
 * Key functionality includes:
 * - Checking the availability of the intention based on the context of the reference using `isAvailable`.
 * - Resolving the reference to determine the missing resource's key and type.
 * - Providing a user interface prompt to input the resource's value, unless running in unit test mode.
 * - Finding the appropriate XML resource file for resource creation, taking into account project structure.
 * - Generating and formatting the new resource XML element.
 * - Enhancing Kotlin files with the necessary import statements for the newly created resource.
 * - Optionally triggering Gradle synchronization to ensure consistent build accessors.
 *
 * This intention operates in a non-write action context (`startInWriteAction` set to false), as user input and
 * resource resolution must occur before modifications are written. Write operations are executed as part of a
 * write command action to ensure proper document changes and undo support.
 *
 * Built-in safeguards include error dialogs for invalid or incomplete project configurations, as well as
 * graceful handling of null or empty user input.
 *
 * Implements `PriorityAction` to ensure this intention is displayed with high priority and `Iconable`
 * to allow custom icons in the IDE's intention menu.
 */
internal class KmpCreateResourceIntention : PsiElementBaseIntentionAction(), PriorityAction, Iconable {

    override fun getPriority(): PriorityAction.Priority = PriorityAction.Priority.TOP

    override fun getIcon(flags: Int): Icon = AllIcons.General.Error

    override fun getFamilyName(): String = KmpResourcesBundle.message("intention.create.resource.family")

    override fun startInWriteAction(): Boolean = false

    @RequiresReadLock
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

        val stringsFile = ReadAction.compute<VirtualFile?, Throwable> {
            val scope = GlobalSearchScope.projectScope(project)
            val detectionService = project.service<ResourceSystemDetectionService>()

            val composeResFiles = FileTypeIndex.getFiles(XmlFileType.INSTANCE, scope).filter { vFile ->
                if (vFile.extension != "xml") return@filter false
                val system = detectionService.detectSystem(vFile)
                vFile.parent?.name == system.valuesDirPrefix && vFile.path.contains(system.baseResourceDirName)
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

    /**
     * Determines the base package name for resource classes from the given Kotlin file.
     *
     * This function scans the import directives of the provided Kotlin file to identify
     * the base package of a resource class. It looks for imports that either end with ".Res"
     * or contain ".generated.resources.", and extracts the substring before the last dot.
     *
     * @param ktFile The Kotlin file to inspect for resource-related import directives.
     * @return The base package name for the resource class if found, or `null` if no valid
     *         resource-related imports are detected.
     */
    private fun getBaseResourcePackage(ktFile: KtFile): String? {
        return ktFile.importDirectives
            .mapNotNull { it.importedFqName?.asString() }
            .find { it.endsWith(".Res") || it.contains(".generated.resources.") }
            ?.substringBeforeLast(".")
    }

    /**
     * Adds an import statement to a Kotlin file if the specified fully qualified name is not already
     * imported. If the exact target or a wildcard import for the base package exists, no changes
     * are made. Otherwise, a new import directive is added to the import list of the file.
     *
     * @param project The current IntelliJ IDEA project context.
     * @param ktFile The Kotlin file where the import should be added; can be null.
     * @param basePackage The base package name to use when constructing the fully qualified name; can be null.
     * @param resolved The resolved resource that contains the key to construct the fully qualified name.
     */
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