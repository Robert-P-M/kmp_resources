package dev.robdoes.kmpresources.presentation.editor.action

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.ui.JBColor
import dev.robdoes.kmpresources.core.KmpResourcesBundle
import dev.robdoes.kmpresources.core.util.LocaleInfo
import dev.robdoes.kmpresources.core.util.LocaleProvider
import javax.swing.Icon
import javax.swing.JComponent

class AddLocaleAction(
    private val onLocaleSelected: (LocaleInfo) -> Unit
) : ComboBoxAction() {

    private val existingLocales = emptySet<String>()
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = KmpResourcesBundle.message("action.table.add.locale.text")
        e.presentation.description = KmpResourcesBundle.message("action.table.add.locale.desc")
        e.presentation.icon = AllIcons.General.Add
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        return DefaultActionGroup()
    }

    override fun createActionPopup(
        context: DataContext,
        component: JComponent,
        disposeCallback: Runnable?
    ): JBPopup {
        val allLocales = LocaleProvider.getAvailableLocales()

        val listStep = object : BaseListPopupStep<LocaleInfo>(
            KmpResourcesBundle.message("action.table.add.locale.popup.title"),
            allLocales
        ) {
            override fun getTextFor(value: LocaleInfo): String {
                val flag = if (value.flagEmoji.isNotEmpty()) "${value.flagEmoji} " else ""
                return "$flag${value.displayName} (${value.languageTag})"
            }

            override fun getIconFor(value: LocaleInfo): Icon? = null

            override fun getForegroundFor(value: LocaleInfo): java.awt.Color? {
                return if (existingLocales.contains(value.languageTag)) JBColor.GRAY
                else super.getForegroundFor(value)
            }

            override fun isSelectable(value: LocaleInfo): Boolean {
                return !existingLocales.contains(value.languageTag)
            }

            override fun onChosen(selectedValue: LocaleInfo, finalChoice: Boolean): PopupStep<*>? {
                if (finalChoice) {
                    return doFinalStep {
                        onLocaleSelected(selectedValue)
                    }
                }
                return FINAL_CHOICE
            }
        }

        val popup = JBPopupFactory.getInstance().createListPopup(listStep)

        if (disposeCallback != null) {
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    disposeCallback.run()
                }
            })
        }

        popup.setMinimumSize(java.awt.Dimension(350, 300))
        return popup
    }
}
