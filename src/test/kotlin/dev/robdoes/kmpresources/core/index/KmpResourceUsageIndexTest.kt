package dev.robdoes.kmpresources.core.index

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.jetbrains.kotlin.idea.base.util.allScope

class KmpResourceUsageIndexTest : BasePlatformTestCase() {

    fun testIndexParsesStandardAndNormalizedKeys() {
        // Arrange: Create a mock Kotlin file containing various resource usages
        val kotlinContent = """
            package dev.robdoes.ui
            val s = Res.string.login_button
            val p = Res.plurals.items_count
            val a = Res.array.country_codes
            // Edge case: In XML "user-name" or "user.name", mapped to "user_name" in Kotlin
            val normal = Res.string.user_name 
        """.trimIndent()

        myFixture.configureByText("MainScreen.kt", kotlinContent)

        // Act & Assert: Verify the index successfully extracted all keys
        assertIndexContains(key = "login_button", expectedOccurrences = 1)
        assertIndexContains(key = "items_count", expectedOccurrences = 1)
        assertIndexContains(key = "country_codes", expectedOccurrences = 1)
        assertIndexContains(key = "user_name", expectedOccurrences = 1)
    }

    fun testIndexIgnoresBuildAndGeneratedFolders() {
        // Arrange
        val content = "val x = Res.string.hidden_key"

        // Add one file to a standard source directory
        myFixture.addFileToProject("src/commonMain/kotlin/App.kt", content)
        // Add another file to a build directory (should be ignored by InputFilter)
        myFixture.addFileToProject("build/generated/Res.kt", content)

        // Act & Assert: The index should only contain the valid source file
        assertIndexContains(key = "hidden_key", expectedOccurrences = 1)
    }

    private fun assertIndexContains(key: String, expectedOccurrences: Int) {
        val files = FileBasedIndex.getInstance().getContainingFiles(
            KMP_RESOURCE_USAGE_INDEX_NAME,
            key,
            project.allScope()
        )

        assertEquals(
            "Index should find exactly $expectedOccurrences file(s) for the key '$key'.",
            expectedOccurrences,
            files.size
        )
    }
}