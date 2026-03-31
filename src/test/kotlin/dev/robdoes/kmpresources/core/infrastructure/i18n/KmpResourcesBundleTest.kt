package dev.robdoes.kmpresources.core.infrastructure.i18n

import com.intellij.testFramework.fixtures.BasePlatformTestCase

internal class KmpResourcesBundleTest : BasePlatformTestCase() {

    fun testMessageReturnsCorrectStringForValidKey() {
        // Act
        // We use a simple, static key that we know exists in the properties file
        val message = KmpResourcesBundle.message("ui.editor.tab.name")

        // Assert
        kotlin.test.assertNotNull(actual = message, message = "The resolved message should not be null")
        kotlin.test.assertEquals(
            expected = "KMP Table Editor",
            actual = message,
            message = "Should return the exact English translation for the key from the properties file"
        )
    }

    fun testMessageFormatsParametersCorrectly() {
        // Act
        // In KmpResourcesBundle.properties: dialog.error.key.exists=Key ''{0}'' already exists.
        val message = KmpResourcesBundle.message("dialog.error.key.exists", "my_test_key")

        // Assert
        kotlin.test.assertEquals(
            expected = "Key 'my_test_key' already exists.",
            actual = message,
            message = "Should correctly format the string and inject the provided parameter"
        )
    }
}