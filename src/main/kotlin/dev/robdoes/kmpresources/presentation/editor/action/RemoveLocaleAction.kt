package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.DumbAware
import dev.robdoes.kmpresources.core.infrastructure.i18n.KmpResourcesBundle
import dev.robdoes.kmpresources.core.shared.LocaleProvider
import javax.swing.JComponent

/**
 * A class that defines an action for removing locales from the active set of locales.
 *
 * This action displays a combo-box dropdown containing a list of currently active locales.
 * The user can select a locale from the list to trigger its removal via the provided callback.
 *
 * @constructor
 * Constructs the `RemoveLocaleAction` with the specified functions for retrieving active locales
 * and handling locale selection.
 *
 * @param getActiveLocales A lambda function returning a set of currently active locale tags in `String` format.
 * @param onLocaleSelected A lambda function invoked when a locale is selected; receives the selected locale tag.
 */
internal class RemoveLocaleAction(
    private val getActiveLocales: () -> Set<String>,
    private val onLocaleSelected: (String) -> Unit
) : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = KmpResourcesBundle.message("action.remove.locale.text")
        e.presentation.icon = AllIcons.General.Remove

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
                    onLocaleSelected(localeTag)
                }
            })
        }
        return group
    }
}