package dev.robdoes.kmpresources.ide.documentation

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.domain.model.ResourceType

/**
 * Represents a documentation target associated with a specific XML tag and key name in the context of KMP (Kotlin Multiplatform) resources.
 *
 * This class is designed to generate documentation for a resource defined in an XML file, such as string resources, plurals,
 * or arrays. It provides mechanisms to compute the presentation, generate documentation content, and manage pointers to itself.
 *
 * @constructor Initializes the documentation target with the provided XML tag and key name.
 * @property xmlTag The XML tag associated with this documentation target.
 * @property keyName The key name of the resource represented by this documentation target.
 */
@Suppress("UnstableApiUsage")
internal class KmpDocumentationTarget(
    val xmlTag: XmlTag,
    val keyName: String
) : DocumentationTarget {

    override fun computePresentation(): TargetPresentation {
        return TargetPresentation.builder(keyName)
            .icon(xmlTag.getIcon(0))
            .presentation()
    }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val tagPointer = SmartPointerManager.getInstance(xmlTag.project).createSmartPsiElementPointer(xmlTag)
        val key = keyName
        return Pointer {
            val restoredTag = tagPointer.element ?: return@Pointer null
            KmpDocumentationTarget(restoredTag, key)
        }
    }

    override fun computeDocumentation(): DocumentationResult? {
        val resourceType = ResourceType.fromXmlTag(xmlTag.name) ?: return null
        val sb = StringBuilder()

        val currentDirName = xmlTag.containingFile.virtualFile?.parent?.name ?: "values"
        val localeName = if (currentDirName.startsWith("values-")) {
            currentDirName.substringAfter("values-")
        } else {
            KmpResourcesBundle.message("doc.popup.locale.default")
        }

        sb.append(DocumentationMarkup.DEFINITION_START)
        sb.append("${KmpResourcesBundle.message("doc.popup.header.resource")} <b>$keyName</b> (${resourceType.xmlTag}) <br>")
        sb.append("${KmpResourcesBundle.message("doc.popup.header.locale")} <i>$localeName</i>")
        sb.append(DocumentationMarkup.DEFINITION_END)

        sb.append(DocumentationMarkup.CONTENT_START)
        sb.append("<b>${KmpResourcesBundle.message("doc.popup.content.value")}</b><br>")

        when (resourceType) {
            ResourceType.String -> sb.append(xmlTag.value.text)
            ResourceType.Plural -> {
                val items = xmlTag.findSubTags("item")
                sb.append("<ul>")
                for (item in items) {
                    val quantity = item.getAttributeValue("quantity") ?: "unknown"
                    sb.append("<li><i>$quantity:</i> ${item.value.text}</li>")
                }
                sb.append("</ul>")
            }

            ResourceType.Array -> {
                val items = xmlTag.findSubTags("item")
                sb.append("<ol>")
                for (item in items) {
                    sb.append("<li>${item.value.text}</li>")
                }
                sb.append("</ol>")
            }
        }
        sb.append(DocumentationMarkup.CONTENT_END)

        val availableLocales = findAvailableLocales(xmlTag, keyName)
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
                sb.append("<a href='locale_$dirName'>[$displayLocale]</a>&nbsp;&nbsp;")
            }
            sb.append(DocumentationMarkup.SECTION_END)
            sb.append(DocumentationMarkup.SECTIONS_END)
        }

        val linkText = KmpResourcesBundle.message("action.goto.kmp.resource.text")
        sb.append(DocumentationMarkup.SECTIONS_START)
        sb.append(DocumentationMarkup.SECTION_HEADER_START)
        sb.append(KmpResourcesBundle.message("doc.popup.section.action"))
        sb.append(DocumentationMarkup.SECTION_SEPARATOR)
        sb.append("<a href='kmp_edit'>$linkText &rarr;</a>")
        sb.append(DocumentationMarkup.SECTION_END)
        sb.append(DocumentationMarkup.SECTIONS_END)

        return DocumentationResult.documentation(sb.toString())
    }


    /**
     * Finds and returns a list of XML tags corresponding to localized variants of the given tag.
     * The method searches for tags in sibling resource directories whose names start with "values".
     *
     * @param currentTag The XML tag for which localized variants are being searched.
     * @param keyName The key name used to match the specific XML tag in localized files.
     * @return A list of XML tags that match the specified tag and key in localized resource directories.
     */
    private fun findAvailableLocales(currentTag: XmlTag, keyName: String): List<XmlTag> {
        val currentFile = currentTag.containingFile.virtualFile ?: return emptyList()
        val composeResourcesDir = currentFile.parent?.parent ?: return emptyList()

        val locales = mutableListOf<XmlTag>()
        val psiManager = PsiManager.getInstance(currentTag.project)

        for (dir in composeResourcesDir.children) {
            if (dir.isDirectory && dir.name.startsWith("values")) {
                val localeXmlFile = dir.findChild(currentFile.name) ?: continue
                val psiFile = psiManager.findFile(localeXmlFile) as? XmlFile ?: continue

                val targetTag = psiFile.rootTag?.subTags?.find {
                    it.name == currentTag.name &&
                            it.getAttributeValue("name")?.replace(".", "_")?.replace("-", "_") == keyName
                }

                if (targetTag != null) locales.add(targetTag)
            }
        }
        return locales.sortedBy { it.containingFile.virtualFile?.parent?.name }
    }
}