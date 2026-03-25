package dev.robdoes.kmpresources.core

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KmpResourcesBundleTest : BasePlatformTestCase() {

    fun testMessageReturnsCorrectStringForValidKey() {
        // Act
        // We use a simple, static key that we know exists in the properties file
        val message = KmpResourcesBundle.message("ui.editor.tab.name")

        // Assert
        assertNotNull(actual = message, message = "The resolved message should not be null")
        assertEquals(
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
        assertEquals(
            expected = "Key 'my_test_key' already exists.",
            actual = message,
            message = "Should correctly format the string and inject the provided parameter"
        )
    }
}