package dev.robdoes.kmpresources.ide.navigation

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.presentation.editor.KmpResourceTableEditor
import javax.swing.Icon

class KmpResourceTarget(
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
        val fileEditorManager = FileEditorManager.getInstance(project)

        val module = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(virtualFile, project)
        val modulePath = module?.name ?: "KMP Module"

        val kmpVirtualFile = dev.robdoes.kmpresources.presentation.editor.KmpResourceVirtualFile(
            modulePath,
            virtualFile
        )

        fileEditorManager.openFile(kmpVirtualFile, requestFocus)

        val rawXmlName = xmlTag.getAttributeValue("name") ?: kotlinKeyName

        ApplicationManager.getApplication().invokeLater {
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