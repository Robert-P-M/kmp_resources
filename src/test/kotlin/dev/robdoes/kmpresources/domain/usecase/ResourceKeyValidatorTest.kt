package dev.robdoes.kmpresources.domain.usecase

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class ResourceKeyValidatorTest {

    @Test
    fun `valid keys should return true`() {
        assertTrue(
            actual = ResourceKeyValidator.isValid("app_name"),
            message = "Standard Android keys with underscores should be valid"
        )
        assertTrue(
            actual = ResourceKeyValidator.isValid("title_123"),
            message = "Keys ending with numbers should be valid"
        )
        assertTrue(
            actual = ResourceKeyValidator.isValid("login_button_text"),
            message = "Longer keys with multiple underscores should be valid"
        )
        assertTrue(
            actual = ResourceKeyValidator.isValid("a"),
            message = "A single letter should be valid"
        )
        assertTrue(
            actual = ResourceKeyValidator.isValid("123"),
            message = "Numeric keys are technically valid in XML"
        )
        assertTrue(
            actual = ResourceKeyValidator.isValid("test_key_99"),
            message = "Mixed alphanumeric keys with underscores should be valid"
        )
    }

    @Test
    fun `invalid keys should return false`() {
        assertFalse(
            actual = ResourceKeyValidator.isValid("App_Name"),
            message = "Uppercase letters are not allowed"
        )
        assertFalse(
            actual = ResourceKeyValidator.isValid("app_Name"),
            message = "CamelCase is not allowed"
        )

        assertFalse(
            actual = ResourceKeyValidator.isValid("app-name"),
            message = "Dashes (-) cause syntax errors in generated Kotlin accessors"
        )
        assertFalse(
            actual = ResourceKeyValidator.isValid("app.name"),
            message = "Dots (.) are not allowed in resource keys"
        )
        assertFalse(
            actual = ResourceKeyValidator.isValid("app name"),
            message = "Spaces are strictly forbidden"
        )

        assertFalse(
            actual = ResourceKeyValidator.isValid("app_name!"),
            message = "Special characters like exclamation marks are not allowed"
        )
        assertFalse(
            actual = ResourceKeyValidator.isValid("äpfel"),
            message = "Umlauts and other non-ASCII characters are not allowed"
        )
    }
}