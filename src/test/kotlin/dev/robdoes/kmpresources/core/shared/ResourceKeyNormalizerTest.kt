package dev.robdoes.kmpresources.core.shared

import org.junit.Test
import kotlin.test.assertEquals

class ResourceKeyNormalizerTest {

    @Test
    fun `normalize should not change keys without dots or dashes`() {
        assertEquals(
            expected = "normal_key_123",
            actual = ResourceKeyNormalizer.normalize("normal_key_123"),
            message = "Keys without dots or dashes should remain completely unchanged"
        )

        assertEquals(
            expected = "camelCaseKey",
            actual = ResourceKeyNormalizer.normalize("camelCaseKey"),
            message = "CamelCase keys should not be modified"
        )
    }

    @Test
    fun `normalize should replace dots with underscores`() {
        assertEquals(
            expected = "key_with_dots",
            actual = ResourceKeyNormalizer.normalize("key.with.dots"),
            message = "Dots in the middle of the string should be replaced with underscores"
        )

        assertEquals(
            expected = "_starts_and_ends_with_dot_",
            actual = ResourceKeyNormalizer.normalize(".starts.and.ends.with.dot."),
            message = "Dots at the beginning and end should also be replaced"
        )
    }

    @Test
    fun `normalize should replace dashes with underscores`() {
        assertEquals(
            expected = "key_with_dashes",
            actual = ResourceKeyNormalizer.normalize("key-with-dashes"),
            message = "Dashes in the middle of the string should be replaced with underscores"
        )

        assertEquals(
            expected = "_starts_and_ends_with_dash_",
            actual = ResourceKeyNormalizer.normalize("-starts-and-ends-with-dash-"),
            message = "Dashes at the beginning and end should also be replaced"
        )
    }

    @Test
    fun `normalize should replace both dots and dashes simultaneously`() {
        assertEquals(
            expected = "mixed_weird_key_format",
            actual = ResourceKeyNormalizer.normalize("mixed.weird-key.format"),
            message = "A combination of dots and dashes should all be converted to underscores"
        )

        assertEquals(
            expected = "_____",
            actual = ResourceKeyNormalizer.normalize(".-.-."),
            message = "A string consisting only of dots and dashes should become only underscores"
        )
    }

    @Test
    fun `normalize should handle empty and blank strings gracefully`() {
        assertEquals(
            expected = "",
            actual = ResourceKeyNormalizer.normalize(""),
            message = "An empty string should return an empty string"
        )

        assertEquals(
            expected = "   ",
            actual = ResourceKeyNormalizer.normalize("   "),
            message = "A string with only whitespaces should remain unchanged"
        )
    }
}