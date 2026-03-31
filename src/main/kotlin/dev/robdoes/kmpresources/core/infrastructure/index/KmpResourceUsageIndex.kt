package dev.robdoes.kmpresources.core.infrastructure.index

import com.intellij.lexer.Lexer
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

/**
 * Identifier (ID) for the KMP Resource Usage Index.
 *
 * This ID is utilized to uniquely represent and manage the `KmpResourceUsageIndex`.
 * It serves as a key for associating index-related operations, such as resource usage
 * analysis and indexing within the context of Kotlin Multiplatform projects.
 *
 * The ID is registered with the specified name and is tied to the internal indexing mechanism.
 */
internal val KMP_RESOURCE_USAGE_INDEX_NAME = ID.create<String, Void>("dev.robdoes.kmpresources.UsageIndex")

/**
 * Provides an implementation of a scalar index for analyzing Kotlin resource usage patterns.
 *
 * This class is responsible for indexing Kotlin code files to identify resource references
 * such as strings, plurals, and arrays. It works in conjunction with IntelliJ's file-based
 * indexing infrastructure to efficiently store and retrieve these resource references.
 *
 * The index captures resource usage following the pattern `Res.string.<resourceName>`,
 * `Res.plurals.<resourceName>`, or `Res.array.<resourceName>`, where `<resourceName>`
 * represents the specific name of the resource being used. This is achieved using a simple
 * state machine lexer over the input content.
 *
 * Key attributes of this class include:
 * - Scanning only Kotlin files while ignoring files in specific directories like `/build/` or `/generated/`.
 * - Dependency on file content changes to update the index.
 * - Providing a unique index name for retrieval and usage in the indexing framework.
 * - Using a simple key descriptor for resource names that ensures efficient serialization.
 */
internal class KmpResourceUsageIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = KMP_RESOURCE_USAGE_INDEX_NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val usages = mutableMapOf<String, Void?>()
            val text = inputData.contentAsText

            val lexer: Lexer = KotlinLexer()
            lexer.start(text)

            var state = 0

            while (lexer.tokenType != null) {
                val tokenType = lexer.tokenType
                val tokenText = lexer.tokenText

                if (tokenType == KtTokens.WHITE_SPACE ||
                    tokenType == KtTokens.EOL_COMMENT ||
                    tokenType == KtTokens.BLOCK_COMMENT
                ) {
                    lexer.advance()
                    continue
                }

                when (state) {
                    0 -> {
                        if (tokenType == KtTokens.IDENTIFIER && tokenText == "Res") state = 1
                    }

                    1 -> {
                        state = if (tokenType == KtTokens.DOT) 2 else 0
                    }

                    2 -> {
                        state =
                            if (tokenType == KtTokens.IDENTIFIER && (tokenText == "string" || tokenText == "plurals" || tokenText == "array")) {
                                3
                            } else {
                                if (tokenType == KtTokens.IDENTIFIER && tokenText == "Res") 1 else 0
                            }
                    }

                    3 -> {
                        state = if (tokenType == KtTokens.DOT) 4 else 0
                    }

                    4 -> {
                        if (tokenType == KtTokens.IDENTIFIER) {
                            usages[tokenText] = null
                        }
                        state = if (tokenType == KtTokens.IDENTIFIER && tokenText == "Res") 1 else 0
                    }
                }

                lexer.advance()
            }
            usages
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 2

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            file.fileType == KotlinFileType.INSTANCE &&
                    !file.path.contains("/build/") &&
                    !file.path.contains("/generated/")
        }
    }

    override fun dependsOnFileContent(): Boolean = true
}
