package dev.robdoes.kmpresources.core.infrastructure.coroutines

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

suspend inline fun <T> withEdtContext(crossinline block: suspend () -> T): T {
    val application = ApplicationManager.getApplication()
    return if (application.isDispatchThread) {
        block()
    } else {
        withContext(Dispatchers.EDT) {
            block()
        }
    }
}

suspend fun Project.awaitSmartMode() {
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