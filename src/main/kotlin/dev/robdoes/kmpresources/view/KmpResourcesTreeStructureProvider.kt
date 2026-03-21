package dev.robdoes.kmpresources.view

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class KmpResourcesTreeStructureProvider : TreeStructureProvider, DumbAware {

    override fun modify(
        parent: AbstractTreeNode<*>,
        children: MutableCollection<AbstractTreeNode<*>>,
        settings: ViewSettings?
    ): MutableCollection<AbstractTreeNode<*>> {

        val project = parent.project ?: return children

        val projectView = ProjectView.getInstance(project)
        if (projectView.currentViewId != KmpResourcesProjectViewPane.ID) {
            return children
        }

        if (parent is ProjectViewProjectNode) {
            val flatModules = mutableListOf<AbstractTreeNode<*>>()
            val baseDir = project.guessProjectDir() ?: return children

            val moduleDirs = findGradleModules(baseDir, baseDir)

            for ((moduleDir, gradlePath) in moduleDirs) {
                flatModules.add(KmpModuleNode(project, moduleDir, gradlePath, settings))
            }

            flatModules.sortBy { (it as KmpModuleNode).name }

            return flatModules
        }

        return children
    }

    /**
     * Sucht rekursiv nach Ordnern, die eine build.gradle.kts (oder build.gradle) enthalten,
     * und generiert daraus den Gradle-Pfad (z.B. ":feature:calendar:month").
     */
    private fun findGradleModules(currentDir: VirtualFile, rootDir: VirtualFile): List<Pair<VirtualFile, String>> {
        val result = mutableListOf<Pair<VirtualFile, String>>()

        if (currentDir.name.startsWith(".") || currentDir.name == "build" || currentDir.name == "gradle") {
            return result
        }

        val isGradleModule =
            currentDir.children?.any { it.name == "build.gradle.kts" || it.name == "build.gradle" } == true

        if (isGradleModule && currentDir != rootDir) {
            var path = currentDir.path.removePrefix(rootDir.path)
            path = path.removePrefix("/")

            result.add(Pair(currentDir, path))
        } else if (isGradleModule && currentDir == rootDir) {
        }
        
        currentDir.children?.forEach { child ->
            if (child.isDirectory) {
                result.addAll(findGradleModules(child, rootDir))
            }
        }

        return result
    }
}


