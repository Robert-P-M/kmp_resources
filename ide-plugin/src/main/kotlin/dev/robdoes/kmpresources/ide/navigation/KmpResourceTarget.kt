package dev.robdoes.kmpresources.ide.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.presentation.editor.KmpResourceTableEditor
import dev.robdoes.kmpresources.presentation.editor.KmpResourceVirtualFile
import kotlinx.coroutines.launch
import javax.swing.Icon

/**
 * Represents a Kotlin Multiplatform resource target that provides a bridge between an XML-tagged resource
 * and the IntelliJ PSI structure. This class extends [FakePsiElement] to integrate with the PSI tree and
 * enable navigation to specific resources within the IDE.
 *
 * @constructor Creates an instance of [KmpResourceTarget].
 * @param xmlTag The [XmlTag] representing the XML resource element this target is associated with.
 * @param kotlinKeyName The key name in Kotlin corresponding to the XML resource element.
 */
internal class KmpResourceTarget(
    private val xmlTag: XmlTag,
    private val kotlinKeyName: String
) : FakePsiElement() {

    override fun getParent(): PsiElement = xmlTag
    override fun getName(): String = kotlinKeyName
    override fun getProject() = xmlTag.project
    override fun getContainingFile(): PsiFile? = xmlTag.containingFile
    override fun isValid() = xmlTag.isValid

    override fun getNavigationElement(): PsiElement = this

    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true

    override fun navigate(requestFocus: Boolean) {
        val virtualFile = xmlTag.containingFile?.virtualFile ?: return
        val project = xmlTag.project
        val fileEditorManager = FileEditorManager.getInstance(project)

        val modulePath = KmpResourceVirtualFile.computeModuleName(
            project,
            virtualFile
        )

        val kmpVirtualFile = KmpResourceVirtualFile(
            modulePath,
            virtualFile
        )

        fileEditorManager.openFile(kmpVirtualFile, requestFocus)

        val rawXmlName = xmlTag.getAttributeValue("name") ?: kotlinKeyName

        project.service<KmpProjectScopeService>()
            .coroutineScope
            .launch {
                val editors = fileEditorManager.getEditors(kmpVirtualFile)
                val tableEditor = editors.find { it is KmpResourceTableEditor } as? KmpResourceTableEditor
                tableEditor?.scrollToKey(rawXmlName)
            }
    }

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText() = kotlinKeyName
            override fun getLocationString() = xmlTag.containingFile?.name
            override fun getIcon(unused: Boolean): Icon? = xmlTag.getIcon(0)
        }
    }

}