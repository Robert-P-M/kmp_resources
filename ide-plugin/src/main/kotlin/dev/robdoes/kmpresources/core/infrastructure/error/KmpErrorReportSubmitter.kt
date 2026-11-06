package dev.robdoes.kmpresources.core.infrastructure.error

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import java.awt.Component
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Opens a pre-filled GitHub issue for exceptions the IDE attributes to this plugin.
 *
 * The plugin version is taken from [getPluginDescriptor], i.e. the descriptor of the plugin that
 * registered this submitter, rather than from a hard-coded plugin id. Reports from a copy of this class
 * in another plugin therefore show that plugin's id and are easy to tell apart.
 */
internal class KmpErrorReportSubmitter : ErrorReportSubmitter() {

    override fun getReportActionText(): String = "Report Issue to Author on GitHub"

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>,
    ): Boolean {
        val event = events.firstOrNull() ?: return false
        val url = buildIssueUrl(
            throwableText = event.throwableText,
            additionalInfo = additionalInfo,
            pluginId = pluginDescriptor?.pluginId?.idString ?: "unknown",
            pluginVersion = pluginDescriptor?.version ?: "unknown",
            ideBuild = ApplicationInfo.getInstance().let { "${it.fullApplicationName} (${it.build.asString()})" },
            os = "${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION} (${SystemInfo.OS_ARCH})",
            jre = "${SystemInfo.JAVA_RUNTIME_VERSION} ${SystemInfo.JAVA_VENDOR}",
        )
        BrowserUtil.browse(url)
        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        return true
    }

    internal companion object {
        const val REPO_URL = "https://github.com/Robert-P-M/kmp_resources"

        /** Browsers and GitHub reject very long URLs; keep the encoded URL well below ~8k characters. */
        private const val MAX_TRACE_CHARS = 4_000

        fun buildIssueUrl(
            throwableText: String,
            additionalInfo: String?,
            pluginId: String,
            pluginVersion: String,
            ideBuild: String,
            os: String,
            jre: String,
        ): String {
            val firstLine = throwableText.lineSequence().firstOrNull()?.trim().orEmpty().ifEmpty { "Unknown exception" }
            val title = "[Crash] ${firstLine.take(80)}"
            val trace = if (throwableText.length > MAX_TRACE_CHARS) {
                throwableText.take(MAX_TRACE_CHARS) + "\n… (truncated, please attach the full idea.log)"
            } else {
                throwableText
            }
            val body = buildString {
                appendLine("### User description")
                appendLine(additionalInfo?.takeIf { it.isNotBlank() } ?: "_No additional information provided._")
                appendLine()
                appendLine("### Environment")
                appendLine("- **Plugin:** $pluginId $pluginVersion")
                appendLine("- **IDE:** $ideBuild")
                appendLine("- **OS:** $os")
                appendLine("- **JRE:** $jre")
                appendLine()
                appendLine("### Stack trace")
                appendLine("```")
                appendLine(trace)
                appendLine("```")
            }
            return "$REPO_URL/issues/new?labels=${encode("type:crash,area:ide-plugin,status:triage")}" +
                "&title=${encode(title)}&body=${encode(body)}"
        }

        private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
