package dev.robdoes.kmpresources.ide.documentation

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.resolver.KmpResourceResolver
import dev.robdoes.kmpresources.domain.model.ResourceType
import dev.robdoes.kmpresources.ide.navigation.KmpResourceTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles special link resolution functionality for KMP (multiplatform) documentation.
 *
 * This implementation resolves custom links in documentation popups, such as navigating to a specific
 * resource or opening an editor for editing the resource directly. The functionality depends on the context
 * of KMP-specific documentation targets.
 *
 * The handler performs the following actions based on the provided URL:
 * - If the URL equals "kmp_edit": Launches a coroutine on the project-scoped coroutine context to close
 *   any open popups and navigates to the resource editor.
 * - If the URL starts with "locale_": Resolves and navigates to a specific locale version of a resource,
 *   based on the target directory name indicated in the URL.
 *
 * For all other URLs or invalid targets, the link resolution returns null.
 */
internal class KmpDocumentationLinkHandler : DocumentationLinkHandler {

    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is KmpDocumentationTarget) return null

        if (url == "kmp_edit") {
            target.xmlTag.project.service<KmpProjectScopeService>().coroutineScope.launch(Dispatchers.EDT) {
                val frame = WindowManager.getInstance().getFrame(target.xmlTag.project)
                if (frame != null) {
                    JBPopupFactory.getInstance().getChildPopups(frame).forEach { it.cancel() }
                }
                KmpResourceTarget(target.xmlTag, target.keyName).navigate(true)
            }
            return null
        }

        if (url.startsWith("locale_")) {
            val targetDirName = url.substringAfter("locale_")
            val resourceType = ResourceType.fromXmlTag(target.xmlTag.name) ?: return null

            val resolved = KmpResourceResolver.ResolvedResource(target.keyName, resourceType)
            val allTags = KmpResourceResolver.findXmlTags(target.xmlTag.project, resolved)

            val newTag = allTags.find {
                it.containingFile.virtualFile?.parent?.name == targetDirName
            } ?: return null

            return LinkResolveResult.resolvedTarget(KmpDocumentationTarget(newTag, target.keyName))
        }

        return null
    }
}