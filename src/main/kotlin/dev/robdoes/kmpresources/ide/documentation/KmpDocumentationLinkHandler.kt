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

class KmpDocumentationLinkHandler : DocumentationLinkHandler {

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