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

/**
 * An implementation of `ErrorReportSubmitter` that handles the submission of error reports
 * by creating a new issue on the GitHub repository associated with the plugin.
 *
 * This class gathers relevant information about the error, including the IDE version, plugin version,
 * and stacktrace, and encodes it into a URL to open the user's default web browser. The user can review
 * and submit the issue on the repository's issue tracker.
 *
 * Key Features:
 * - Provides a custom action text to display in the error reporting UI.
 * - Collects additional user-provided information along with IDE and plugin metadata.
 * - Formats the collected data into a GitHub-compatible issue template.
 * - Opens a new issue URL in the default browser for user review and submission.
 *
 * This reporter is specific to the `kmp_resources` plugin and utilizes its GitHub repository for tracking issues.
 */
internal class KmpErrorReportSubmitter : ErrorReportSubmitter() {

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