package dev.robdoes.kmpresources.core.infrastructure.index

import com.intellij.lexer.Lexer
import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KotlinLexer
import org.jetbrains.kotlin.lexer.KtTokens

val KMP_RESOURCE_USAGE_INDEX_NAME = ID.create<String, Void>("dev.robdoes.kmpresources.UsageIndex")

class KmpResourceUsageIndex : ScalarIndexExtension<String>() {

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
