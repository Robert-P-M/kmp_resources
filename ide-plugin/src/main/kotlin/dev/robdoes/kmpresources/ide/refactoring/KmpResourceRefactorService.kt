package dev.robdoes.kmpresources.ide.refactoring

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
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
import dev.robdoes.kmpresources.core.application.service.ResourceSystemDetectionService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.withEdtContext
import dev.robdoes.kmpresources.core.shared.ResourceKeyNormalizer
import dev.robdoes.kmpresources.presentation.editor.search.KmpUsageSearchScope
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Provides services for refactoring resource keys in Kotlin Multiplatform (KMP) projects.
 * This includes updating the resource key in XML files and propagating the change to related Kotlin code files.
 */
internal object KmpResourceRefactorService {

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

    /**
     * Finds Kotlin files affected by the given old resource key within the specific module of a project.
     *
     * This method identifies all Kotlin source files in the module scope that:
     * 1. Reference the given resource key (in its normalized form).
     * 2. Belong to the module in which the specified XML file resides.
     * 3. Are not located in excluded paths (e.g., `/generated/`, `/build/`).
     *
     * The identification is achieved by utilizing a custom search scope and
     * leveraging the IntelliJ Platform's PSI search capabilities.
     *
     * @param project The IntelliJ project instance in which the search is performed.
     * @param xmlFile The virtual file representing the XML file, used to determine the associated module.
     * @param oldKey The resource key to search for, which will be normalized before usage.
     * @return A list of smart pointers pointing to the affected Kotlin files, or null if the module cannot be determined.
     */
    private suspend fun findAffectedFiles(
        project: Project,
        xmlFile: VirtualFile,
        oldKey: String
    ): List<SmartPsiElementPointer<KtFile>>? {
        val module = readAction { ModuleUtilCore.findModuleForFile(xmlFile, project) } ?: return null



        return readAction {
            val moduleScope = GlobalSearchScope.moduleScope(module)
            val filteredScope = KmpUsageSearchScope(moduleScope)

            val normalizedOldKey = ResourceKeyNormalizer.normalize(oldKey)
            val pointers = mutableListOf<SmartPsiElementPointer<KtFile>>()
            val psiManager = PsiManager.getInstance(project)
            val pointerManager = SmartPointerManager.getInstance(project)

            PsiSearchHelper.getInstance(project).processAllFilesWithWord(
                normalizedOldKey,
                filteredScope,
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


/**
 * Provides functionality to update the name attribute of a specified resource key in an XML file.
 *
 * This utility is primarily used for modifying resource keys within XML resource files in a given project.
 * It identifies the corresponding resource type and key, and updates the key to a new value.
 *
 * ### Responsibilities:
 * - Locates the target XML tag based on the specified `resourceType` and `oldKey`.
 * - Updates the `name` attribute of the identified tag to the provided `newKey`.
 *
 * @constructor Creates an instance of the XmlResourceUpdater object.
 */
private object XmlResourceUpdater {
    /**
     * Updates a resource key within an XML file for a specified resource type.
     *
     * This function locates the XML tag corresponding to the given resource type and old key,
     * then updates its "name" attribute to the new key.
     *
     * @param project The IntelliJ project within which the resource key should be updated.
     * @param xmlFile The virtual file representing the XML file to be updated.
     * @param resourceType The type of the resource (e.g., "string", "string-array", etc.).
     * @param oldKey The existing resource key to be replaced.
     * @param newKey The new resource key to replace the old one.
     */
    fun updateKey(project: Project, xmlFile: VirtualFile, resourceType: String, oldKey: String, newKey: String) {
        val psiXmlFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile
        val targetTag = psiXmlFile?.rootTag?.subTags?.find {
            it.name == resourceType && it.getAttributeValue("name") == oldKey
        }
        targetTag?.setAttribute("name", newKey)
    }
}

/**
 * Provides functionality to update usage references of resource keys in Kotlin files.
 *
 * This object is responsible for identifying and replacing references to an old resource key with a new one
 * in a given Kotlin file. It uses the detected resource system and import contexts to ensure accurate
 * updates within the file's scope.
 */
private object KotlinUsageUpdater {

    /**
     * Updates all usages of a resource key in the given Kotlin file.
     *
     * This method identifies and replaces all references to the specified old resource key
     * within the provided Kotlin file, using the new resource key. It handles cases where
     * the old key is explicitly imported or referenced through a fully qualified name.
     *
     * @param ktFile The Kotlin file where usages of the resource key need to be updated.
     * @param psiFactory A factory instance for creating Kotlin PSI elements.
     * @param resourceType The type of the resource (e.g., "string", "string-array").
     * @param oldKey The name of the resource key to be replaced.
     * @param newKey The new name of the resource key to replace the old one.
     */
    fun updateUsages(ktFile: KtFile, psiFactory: KtPsiFactory, resourceType: String, oldKey: String, newKey: String) {
        val detectionService = ktFile.project.service<ResourceSystemDetectionService>()
        val system = detectionService.detectSystem(ktFile.virtualFile)

        val ktType = if (resourceType == "string-array") "array" else resourceType

        val fullOldReference = "${system.kotlinReferenceClass}.$ktType.$oldKey"

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

/**
 * Utility object for updating import statements in Kotlin files.
 *
 * This object provides functionality for replacing occurrences of an old import key
 * with a new import key in a Kotlin file. It is designed to work as part
 * of a larger resource renaming operation, ensuring that import directives
 * and relevant references in the file are updated consistently.
 */
private object KotlinImportUpdater {
    /**
     * Updates the import directives in the given Kotlin file by replacing occurrences of the old key with the new key.
     *
     * This method searches for all import directives in the specified Kotlin file that reference the given old key.
     * Any matching import directives are updated by replacing the corresponding key with the new key. Only the
     * last occurrence of the old key in each matching directive is replaced.
     *
     * @param ktFile The Kotlin file whose import directives are to be updated.
     * @param psiFactory A factory instance used to create new PSI elements, such as the updated key.
     * @param oldKey The existing resource key to be replaced in the import directives.
     * @param newKey The new resource key to replace the old key.
     */
    fun updateImports(ktFile: KtFile, psiFactory: KtPsiFactory, oldKey: String, newKey: String) {
        val imports = ktFile.importDirectives.filter { it.importedName?.asString() == oldKey }
        for (import in imports) {
            val importedRef = import.importedReference ?: continue
            val nameExpressions = importedRef.collectDescendantsOfType<KtNameReferenceExpression> { it.text == oldKey }
            nameExpressions.lastOrNull()?.replace(psiFactory.createNameIdentifier(newKey))
        }
    }
}