package dev.robdoes.kmpresources.view

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class KmpModuleNode(
    project: Project,
    private val moduleDir: VirtualFile,
    private val moduleDisplayName: String,
    settings: ViewSettings?
) : ProjectViewNode<VirtualFile>(project, moduleDir, settings) {

    companion object {
        private const val DIR_SRC = "src"
        private const val DIR_COMMON_MAIN = "commonMain"
        private const val DIR_COMPOSE_RESOURCES = "composeResources"
    }

    override fun update(presentation: PresentationData) {
        presentation.presentableText = moduleDisplayName
        presentation.setIcon(AllIcons.Nodes.Module)
    }

    override fun getChildren(): MutableCollection<out ProjectViewNode<*>> {
        val project = myProject ?: return mutableListOf()
        val psiManager = PsiManager.getInstance(project)

        val composeResourcesDir = moduleDir
            .findChild(DIR_SRC)
            ?.findChild(DIR_COMMON_MAIN)
            ?.findChild(DIR_COMPOSE_RESOURCES)

        if (composeResourcesDir == null || !composeResourcesDir.isDirectory) {
            return mutableListOf()
        }

        val children = mutableListOf<ProjectViewNode<*>>()

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

        return children
    }

    override fun contains(file: VirtualFile): Boolean {
        return VfsUtilCore.isAncestor(moduleDir, file, false)
    }
}