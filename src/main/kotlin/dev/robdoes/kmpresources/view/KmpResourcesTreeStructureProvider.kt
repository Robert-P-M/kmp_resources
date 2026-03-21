package dev.robdoes.kmpresources.view

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class KmpResourcesTreeStructureProvider : TreeStructureProvider, DumbAware {

    companion object {
        private const val DIR_BUILD = "build"
        private const val DIR_GRADLE = "gradle"
        private const val DIR_SRC = "src"
        private const val DIR_COMMON_MAIN = "commonMain"
        private const val DIR_COMPOSE_RESOURCES = "composeResources"

        private const val FILE_BUILD_GRADLE_KTS = "build.gradle.kts"
        private const val FILE_BUILD_GRADLE = "build.gradle"
    }

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

            val moduleDirs = findGradleModules(baseDir)

            val hideEmptyModules = PropertiesComponent.getInstance(project)
                .getBoolean(KmpResourcesProjectViewPane.HIDE_EMPTY_MODULES_KEY, false)

            for ((moduleDir, gradlePath) in moduleDirs) {
                if (hideEmptyModules && !hasComposeResources(moduleDir)) {
                    continue
                }

                flatModules.add(KmpModuleNode(project, moduleDir, gradlePath, settings))
            }

            flatModules.sortBy { (it as KmpModuleNode).name }

            return flatModules
        }

        return children
    }

    private fun findGradleModules(rootDir: VirtualFile): List<Pair<VirtualFile, String>> {
        val result = mutableListOf<Pair<VirtualFile, String>>()
        val queue = ArrayDeque<VirtualFile>()

        queue.add(rootDir)

        while (queue.isNotEmpty()) {
            val currentDir = queue.removeFirst()

            if (currentDir.name.startsWith(".") || currentDir.name == DIR_BUILD || currentDir.name == DIR_GRADLE) {
                continue
            }

            val children = currentDir.children ?: continue
            val isGradleModule = children.any { it.name == FILE_BUILD_GRADLE_KTS || it.name == FILE_BUILD_GRADLE }

            if (isGradleModule && currentDir != rootDir) {
                val relativePath = VfsUtilCore.getRelativePath(currentDir, rootDir, '/')
                if (relativePath != null) {
                    val gradlePath = ":" + relativePath.replace('/', ':')
                    result.add(Pair(currentDir, gradlePath))
                }
            }

            children.forEach { child ->
                if (child.isDirectory) {
                    queue.add(child)
                }
            }
        }

        return result
    }

    private fun hasComposeResources(moduleDir: VirtualFile): Boolean {
        val composeResourcesDir = moduleDir
            .findChild(DIR_SRC)
            ?.findChild(DIR_COMMON_MAIN)
            ?.findChild(DIR_COMPOSE_RESOURCES)

        return composeResourcesDir != null && composeResourcesDir.isDirectory
    }
}


