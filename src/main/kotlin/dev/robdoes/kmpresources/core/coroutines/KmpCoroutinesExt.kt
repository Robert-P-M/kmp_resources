package dev.robdoes.kmpresources.core.coroutines

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend inline fun <T> withEdtContext(crossinline block: () -> T): T {
    val application = ApplicationManager.getApplication()
    return if (application.isDispatchThread) {
        block()
    } else {
        withContext(Dispatchers.EDT) {
            block()
        }
    }
}