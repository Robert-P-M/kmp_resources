package dev.robdoes.kmpresources.ide.linter

import dev.robdoes.kmpresources.ide.linter.FormatArgumentAnalyzer.countRequiredArguments
import org.junit.Test
import kotlin.test.assertEquals

internal class FormatArgumentAnalyzerTest {

    @Test
    fun `strings without format arguments should return 0`() {
        assertEquals(
            expected = 0,
            actual = countRequiredArguments("Hello World!"),
            message = "A normal string without placeholders should require 0 arguments"
        )
        assertEquals(
            expected = 0,
            actual = countRequiredArguments("100% discount"),
            message = "A single % symbol without 's' or 'd' is not a valid format argument"
        )
    }

    @Test
    fun `escaped percent signs should be ignored`() {
        assertEquals(
            expected = 0,
            actual = countRequiredArguments("Progress: 50%%s"),
            message = "An escaped percent sign (%%s) should not be counted as a format argument"
        )
        assertEquals(
            expected = 1,
            actual = countRequiredArguments("Progress %%s is %d"),
            message = "Only the unescaped %d should be counted"
        )
    }

    @Test
    fun `unnumbered arguments should be counted correctly`() {
        assertEquals(
            expected = 1,
            actual = countRequiredArguments("Hello %s"),
            message = "A single %s should require 1 argument"
        )
        assertEquals(
            expected = 2,
            actual = countRequiredArguments("%s and %d"),
            message = "Two unnumbered placeholders should require 2 arguments"
        )
    }

    @Test
    fun `positional arguments should return the highest index`() {
        assertEquals(
            expected = 1,
            actual = countRequiredArguments("Hello %1\$s"),
            message = "Positional index 1 should require 1 argument"
        )
        assertEquals(
            expected = 3,
            actual = countRequiredArguments("%3\$s %1\$d %2\$s"),
            message = "The highest index is 3, so 3 arguments are expected regardless of order"
        )
    }
}