package dev.robdoes.kmpresources.core.infrastructure.error

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.Consumer
import java.awt.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class KmpErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText(): String {
        return "Report Issue to Author on GitHub"
    }

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val event = events.firstOrNull() ?: return false

        val exceptionTitle = event.throwableText.lines().firstOrNull() ?: "Unknown Exception"

        val pluginId = PluginId.getId("dev.robdoes.kmpresources")
        val pluginVersion = PluginManagerCore.getPlugin(pluginId)?.version ?: "Unknown"
        val ideVersion = ApplicationInfo.getInstance().build.asString()

        val issueTitle = "[Crash] ${exceptionTitle.take(65)}"

        val issueBody = buildString {
            appendLine("### User Description")
            appendLine(additionalInfo?.takeIf { it.isNotBlank() } ?: "No additional information provided by the user.")
            appendLine()
            appendLine("### Environment")
            appendLine("- **Plugin Version:** $pluginVersion")
            appendLine("- **IDE Version:** $ideVersion")
            appendLine()
            appendLine("### Stacktrace")
            appendLine("```java")
            appendLine(event.throwableText)
            appendLine("```")
        }

        val repoUrl = "https://github.com/Robert-P-M/kmp_resources"

        val encodedTitle = URLEncoder.encode(issueTitle, StandardCharsets.UTF_8.toString())
        val encodedBody = URLEncoder.encode(issueBody, StandardCharsets.UTF_8.toString())

        val url = "$repoUrl/issues/new?title=$encodedTitle&body=$encodedBody"

        BrowserUtil.browse(url)

        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))

        return true
    }
}