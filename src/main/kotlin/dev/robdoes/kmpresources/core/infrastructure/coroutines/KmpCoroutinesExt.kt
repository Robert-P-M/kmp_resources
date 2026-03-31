package dev.robdoes.kmpresources.core.infrastructure.coroutines

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Executes the given suspending [block] on the EDT (Event Dispatch Thread) if the current thread is not the dispatch thread.
 * If already on the dispatch thread, the [block] is executed directly without switching contexts.
 *
 * @param block A suspending lambda to be executed within the EDT context if the current thread is not the dispatch thread.
 * @return The result of the executed [block].
 */
internal suspend inline fun <T> withEdtContext(crossinline block: suspend () -> T): T {
    val application = ApplicationManager.getApplication()
    return if (application.isDispatchThread) {
        block()
    } else {
        withContext(Dispatchers.EDT) {
            block()
        }
    }
}

/**
 * Suspends the current coroutine until the IntelliJ IDEA indexing process (dumb mode)
 * for the given project is completed and transitions to a smart mode.
 *
 * This method uses a cancellable coroutine to wait for the project indexing to finish.
 * If the project is already in smart mode when this method is called, it returns immediately.
 *
 * Note: This method is internal and should be used cautiously to ensure it aligns with
 * the desired behavior regarding project state and coroutine management in the context of IntelliJ IDEA plugins.
 *
 * @receiver The [Project] instance for which to await the transition to smart mode.
 */
internal suspend fun Project.awaitSmartMode() {
    val dumbService = DumbService.getInstance(this)
    if (!dumbService.isDumb) return

    suspendCancellableCoroutine { continuation ->
        dumbService.runWhenSmart {
            if (continuation.isActive) {
                continuation.resumeWith(Result.success(Unit))
            }
        }
    }
}