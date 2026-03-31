package dev.robdoes.kmpresources.core.infrastructure.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

/**
 * The base name of the resource bundle used for retrieving localized messages.
 * This constant is annotated with `@NonNls` to indicate that its value is not subject to localization.
 */
@NonNls
private const val BUNDLE = "messages.KmpResourcesBundle"

/**
 * Provides localized messages from a resource bundle in a KMP (Kotlin Multiplatform) IntelliJ plugin.
 *
 * This object acts as a utility for fetching localized strings defined in the associated resource bundle.
 * It extends `DynamicBundle` to enable dynamic localization while providing an idiomatic way to access strings.
 *
 * The primary use case is to retrieve messages or strings using resource keys at runtime.
 */
internal object KmpResourcesBundle : DynamicBundle(BUNDLE) {

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}