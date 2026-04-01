package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleProvider
import dev.robdoes.kmpresources.domain.usecase.LocaleFormatValidator
import javax.swing.JComponent

internal class EditLocaleAction(
    private val project: Project,
    private val getActiveLocales: () -> Set<String>,
    private val onLocaleEdited: (String, String) -> Unit
) : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = KmpResourcesBundle.message("action.edit.locale.text")
        e.presentation.icon = AllIcons.Actions.Edit
        e.presentation.isEnabled = getActiveLocales().isNotEmpty()
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val locales = getActiveLocales().sorted()

        for (localeTag in locales) {
            val localeInfo = LocaleProvider.getAvailableLocales().find { it.languageTag == localeTag }
            val flag = localeInfo?.flagEmoji?.let { "$it " } ?: ""
            val name = localeInfo?.displayName ?: localeTag

            group.add(object : AnAction("$flag$name ($localeTag)", null, AllIcons.FileTypes.Xml) {
                override fun actionPerformed(e: AnActionEvent) {
                    promptForNewLocale(localeTag, getActiveLocales())
                }
            })
        }
        return group
    }

    private fun promptForNewLocale(oldLocale: String, currentLocales: Set<String>) {
        val availableRealLocales = LocaleProvider.getAvailableLocales().map { it.languageTag }.toSet()

        val validator = object : InputValidatorEx {

            override fun checkInput(inputString: String?): Boolean {
                if (inputString.isNullOrBlank()) return false
                if (inputString == oldLocale) return false
                if (currentLocales.contains(inputString)) return false
                if (!LocaleFormatValidator.isValid(inputString)) return false

                if (!availableRealLocales.contains(inputString)) return false

                return true
            }

            override fun canClose(inputString: String?): Boolean = checkInput(inputString)

            override fun getErrorText(inputString: String?): String? {
                if (inputString.isNullOrBlank()) return KmpResourcesBundle.message("dialog.edit.locale.error.empty")
                if (inputString == oldLocale) return KmpResourcesBundle.message("dialog.edit.locale.error.same")
                if (currentLocales.contains(inputString)) return KmpResourcesBundle.message("dialog.edit.locale.error.exists")
                if (!LocaleFormatValidator.isValid(inputString)) return KmpResourcesBundle.message("dialog.edit.locale.error.format")

                if (!availableRealLocales.contains(inputString)) return KmpResourcesBundle.message("dialog.edit.locale.error.unknown")

                return null
            }
        }

        val newLocale = Messages.showInputDialog(
            project,
            KmpResourcesBundle.message("dialog.edit.locale.message", oldLocale),
            KmpResourcesBundle.message("dialog.edit.locale.title"),
            Messages.getQuestionIcon(),
            oldLocale,
            validator
        )

        if (newLocale != null && newLocale != oldLocale) {
            onLocaleEdited(oldLocale, newLocale)
        }
    }
}