package dev.robdoes.kmpresources.view

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.IconManager
import com.intellij.util.PlatformIcons

class KmpModuleNode(
    project: Project,
    private val moduleDir: VirtualFile,
    private val moduleDisplayName: String,
    settings: ViewSettings?
) : ProjectViewNode<VirtualFile>(project, moduleDir, settings) {

    override fun update(presentation: PresentationData) {
        presentation.presentableText = moduleDisplayName
        presentation.setIcon(PlatformIcons.FOLDER_ICON)
    }

    override fun getChildren(): MutableCollection<out ProjectViewNode<*>> {
        val children = mutableListOf<ProjectViewNode<*>>()
        val project = myProject ?: return children
        val psiManager = PsiManager.getInstance(project)

        // Suche den composeResources Ordner innerhalb des Moduls
        val composeResourcesDir = moduleDir
            .findChild("src")
            ?.findChild("commonMain")
            ?.findChild("composeResources")

        if (composeResourcesDir != null && composeResourcesDir.isDirectory) {
            // WENN der Ordner existiert, zeigen wir NUR dessen Inhalt an (z.B. values, drawable)
            composeResourcesDir.children?.forEach { childFile ->
                if (!childFile.name.startsWith(".")) {
                    val psiDir = psiManager.findDirectory(childFile)
                    if (psiDir != null) {
                        children.add(PsiDirectoryNode(project, psiDir, settings))
                    } else {
                        val psiFile = psiManager.findFile(childFile)
                        if (psiFile != null) {
                            children.add(PsiFileNode(project, psiFile, settings))
                        }
                    }
                }
            }
        } else {
            // WENN der Ordner (noch) NICHT existiert:
            // Geben wir vorerst eine leere Liste zurück.
            // Später können wir hier eine UI (z.B. ein spezielles Icon/Item) einbauen,
            // auf das der User klicken kann, um den Ordner anlegen zu lassen.
            return mutableListOf()
        }

        return children
    }

    override fun contains(file: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current == moduleDir) return true
            current = current.parent
        }
        return false
    }
}