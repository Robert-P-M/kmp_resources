package dev.robdoes.kmpresources.core.infrastructure.error

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertTrue

class KmpErrorReportSubmitterTest {

    private fun url(trace: String) = KmpErrorReportSubmitter.buildIssueUrl(
        throwableText = trace,
        additionalInfo = null,
        pluginId = "dev.robdoes.kmpresources",
        pluginVersion = "2.0.0",
        ideBuild = "IntelliJ IDEA (IU-253.1)",
        os = "Mac OS X 15 (aarch64)",
        jre = "21.0.5 JetBrains",
    )

    @Test
    fun `contains plugin id, version and environment`() {
        val decoded = URLDecoder.decode(url("java.lang.IllegalStateException: boom\n\tat a.b.C"), StandardCharsets.UTF_8)
        assertTrue(decoded.contains("title=[Crash] java.lang.IllegalStateException: boom"))
        assertTrue(decoded.contains("**Plugin:** dev.robdoes.kmpresources 2.0.0"))
        assertTrue(decoded.contains("**OS:** Mac OS X 15 (aarch64)"))
        assertTrue(decoded.contains("labels=type:crash,area:ide-plugin,status:triage"))
    }

    @Test
    fun `long stack traces are truncated to keep the URL usable`() {
        val trace = "java.lang.RuntimeException\n" + "\tat x.Y.z(Y.kt:1)\n".repeat(2_000)
        val result = url(trace)
        assertTrue(result.length < 8_000, "URL length was ${result.length}")
        assertTrue(URLDecoder.decode(result, StandardCharsets.UTF_8).contains("truncated"))
    }
}
