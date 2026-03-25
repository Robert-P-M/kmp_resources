package dev.robdoes.kmpresources.core.index

import com.intellij.util.indexing.*
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor
import org.jetbrains.kotlin.idea.KotlinFileType


val KMP_RESOURCE_USAGE_INDEX_NAME = ID.create<String, Void>("dev.robdoes.kmpresources.UsageIndex")

private val RESOURCE_USAGE_PATTERN = """Res\.(string|plurals|array)\.([a-z0-9_]+)""".toRegex()

class KmpResourceUsageIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = KMP_RESOURCE_USAGE_INDEX_NAME

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { inputData ->
            val text = inputData.contentAsText
            val usages = mutableMapOf<String, Void?>()

            RESOURCE_USAGE_PATTERN.findAll(text).forEach { match ->
                val key = match.groups[2]?.value
                if (key != null) {
                    usages[key] = null
                }
            }
            usages
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getVersion(): Int = 1

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            file.fileType == KotlinFileType.INSTANCE &&
                    !file.path.contains("/build/") &&
                    !file.path.contains("/generated/")
        }
    }

    override fun dependsOnFileContent(): Boolean = true
}
