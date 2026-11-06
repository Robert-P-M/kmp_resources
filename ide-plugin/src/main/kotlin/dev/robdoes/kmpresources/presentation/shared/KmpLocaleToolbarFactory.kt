package dev.robdoes.kmpresources.presentation.shared

import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.presentation.editor.action.AddLocaleAction
import dev.robdoes.kmpresources.presentation.editor.action.EditLocaleAction
import dev.robdoes.kmpresources.presentation.editor.action.RemoveLocaleAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Factory object responsible for adding locale management actions to a toolbar.
 * This includes actions for adding, removing, and editing locales.
 */
internal object KmpLocaleToolbarFactory {

    /**
     * Adds actions for managing locales to the specified action group.
     * The actions include adding, removing, and editing locales in a project.
     *
     * @param group The action group to which locale-related actions will be added.
     * @param project The current project context.
     * @param localeHandler The handler responsible for performing locale-related operations such as adding, removing, and renaming locales.
     * @param getActiveLocales A lambda that returns a set of currently active locales.
     * @param onLocalesChanged A callback invoked whenever the set of active locales is modified.
     */
    fun addLocaleActions(
        group: DefaultActionGroup,
        project: Project,
        localeHandler: KmpLocaleInteractionHandler,
        getActiveLocales: () -> Set<String>,
        onLocalesChanged: () -> Unit
    ) {
        group.add(
            AddLocaleAction { selectedLocale ->
                project.service<KmpProjectScopeService>().coroutineScope.launch {
                    if (localeHandler.addLocale(selectedLocale.languageTag)) {
                        withContext(Dispatchers.EDT) { onLocalesChanged() }
                    }
                }
            }
        )

        group.addSeparator()

        group.add(
            RemoveLocaleAction(
                getActiveLocales = getActiveLocales,
                onLocaleSelected = { selectedLocale ->
                    project.service<KmpProjectScopeService>().coroutineScope.launch {
                        if (localeHandler.removeLocaleGlobal(selectedLocale)) {
                            withContext(Dispatchers.EDT) { onLocalesChanged() }
                        }
                    }
                }
            )
        )

        group.addSeparator()

        group.add(
            EditLocaleAction(
                project = project,
                getActiveLocales = getActiveLocales,
                onLocaleEdited = { oldLocale, newLocale ->
                    project.service<KmpProjectScopeService>().coroutineScope.launch {
                        if (localeHandler.renameLocaleGlobal(oldLocale, newLocale)) {
                            withContext(Dispatchers.EDT) { onLocalesChanged() }
                        }
                    }
                }
            )
        )
    }
}