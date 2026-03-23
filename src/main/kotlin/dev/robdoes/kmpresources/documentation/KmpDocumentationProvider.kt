package dev.robdoes.kmpresources.documentation

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.KmpResourcesBundle
import dev.robdoes.kmpresources.navigation.KmpResourceTarget
import dev.robdoes.kmpresources.util.KmpResourceResolver

class KmpDocumentationProvider : AbstractDocumentationProvider() {

    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        val resolved = KmpResourceResolver.resolveReference(contextElement ?: return null) ?: return null
        val tags = KmpResourceResolver.findXmlTags(contextElement.project, resolved)

        if (tags.isEmpty()) return null

        return KmpResourceTarget(tags.first(), resolved.key)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element !is KmpResourceTarget) return null

        val tag = element.parent as? XmlTag ?: return null
        val resourceType = tag.name

        val sb = StringBuilder()

        val currentDirName = tag.containingFile.virtualFile?.parent?.name ?: "values"
        val localeName = if (currentDirName.startsWith("values-")) {
            currentDirName.substringAfter("values-")
        } else {
            KmpResourcesBundle.message("doc.popup.locale.default")
        }

        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("${KmpResourcesBundle.message("doc.popup.header.resource")} <b>${element.name}</b> ($resourceType) <br>")
        sb.append("${KmpResourcesBundle.message("doc.popup.header.locale")} <i>$localeName</i>")
        sb.append(DocumentationMarkup.DEFINITION_END)

        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append("<b>${KmpResourcesBundle.message("doc.popup.content.value")}</b><br>")

        when (resourceType) {
            "string" -> {
                sb.append(tag.value.text)
            }

            "plurals" -> {
                val items = tag.findSubTags("item")
                sb.append("<ul>")
                for (item in items) {
                    val quantity = item.getAttributeValue("quantity") ?: "unknown"
                    sb.append("<li><i>$quantity:</i> ${item.value.text}</li>")
                }
                sb.append("</ul>")
            }

            "string-array" -> {
                val items = tag.findSubTags("item")
                sb.append("<ol>")
                for (item in items) {
                    sb.append("<li>${item.value.text}</li>")
                }
                sb.append("</ol>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)

        val availableLocales = findAvailableLocales(tag, element.name)
        if (availableLocales.isNotEmpty()) {
            sb.append(DocumentationMarkup.SECTIONS_START)
            sb.append(DocumentationMarkup.SECTION_HEADER_START)
            sb.append(KmpResourcesBundle.message("doc.popup.section.translations"))
            sb.append(DocumentationMarkup.SECTION_SEPARATOR)

            for (localeTag in availableLocales) {
                val dirName = localeTag.containingFile.virtualFile?.parent?.name ?: continue
                val displayLocale = if (dirName == "values") {
                    KmpResourcesBundle.message("doc.popup.locale.default")
                } else {
                    dirName.substringAfter("values-")
                }

                val targetPath = localeTag.containingFile.virtualFile?.path
                if (targetPath != null) {
                    sb.append("<a href='psi_element://locale_$targetPath'>[$displayLocale]</a>&nbsp;&nbsp;")
                }
            }
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }

        val linkText = KmpResourcesBundle.message("action.goto.kmp.resource.text")
        sb.append(DocumentationMarkup.SECTIONS_START)
        sb.append(DocumentationMarkup.SECTION_HEADER_START)
        sb.append(KmpResourcesBundle.message("doc.popup.section.action"))
        sb.append(DocumentationMarkup.SECTION_SEPARATOR)
        sb.append("<a href='psi_element://kmp_edit'>$linkText &rarr;</a>")
        sb.append(DocumentationMarkup.SECTION_END)
        sb.append(DocumentationMarkup.SECTIONS_END)

        return sb.toString()
    }

    private fun findAvailableLocales(currentTag: XmlTag, keyName: String): List<XmlTag> {
        val currentFile = currentTag.containingFile.virtualFile ?: return emptyList()
        val valuesDir = currentFile.parent ?: return emptyList()
        val composeResourcesDir = valuesDir.parent ?: return emptyList()

        val locales = mutableListOf<XmlTag>()
        val psiManager = PsiManager.getInstance(currentTag.project)

        for (dir in composeResourcesDir.children) {
            if (dir.isDirectory && dir.name.startsWith("values")) {
                val xmlFileName = currentFile.name
                val localeXmlFile = dir.findChild(xmlFileName) ?: continue
                val psiFile = psiManager.findFile(localeXmlFile) as? XmlFile ?: continue
                val rootTag = psiFile.rootTag ?: continue

                val targetTag = rootTag.subTags.find {
                    it.name == currentTag.name &&
                            it.getAttributeValue("name")?.replace(".", "_")?.replace("-", "_") == keyName
                }

                if (targetTag != null) {
                    locales.add(targetTag)
                }
            }
        }
        return locales.sortedBy { it.containingFile.virtualFile?.parent?.name }
    }

    override fun getDocumentationElementForLink(
        psiManager: PsiManager?,
        link: String?,
        context: PsiElement?
    ): PsiElement? {
        if (link == null || context == null) return super.getDocumentationElementForLink(psiManager, link, context)

        if (link == "kmp_edit") {
            val target = context as? KmpResourceTarget ?: return null
            getApplication().invokeLater {
                val frame = WindowManager.getInstance().getFrame(target.project)
                if (frame != null) {
                    val popups = JBPopupFactory.getInstance().getChildPopups(frame)
                    popups.forEach { it.cancel() }
                }
                target.navigate(true)
            }
            return null
        }

        if (link.startsWith("locale_")) {
            val targetPath = link.substringAfter("locale_")
            val project = context.project
            val file = LocalFileSystem.getInstance().findFileByPath(targetPath) ?: return null
            val psiFile = PsiManager.getInstance(project).findFile(file) as? XmlFile ?: return null

            val originalTarget = context as? KmpResourceTarget ?: return null
            val originalXmlTag = originalTarget.parent as? XmlTag ?: return null

            val targetTag = psiFile.rootTag?.subTags?.find {
                it.name == originalXmlTag.name &&
                        it.getAttributeValue("name")?.replace(".", "_")?.replace("-", "_") == originalTarget.name
            } ?: return null

            return KmpResourceTarget(targetTag, originalTarget.name)
        }

        return super.getDocumentationElementForLink(psiManager, link, context)
    }
}