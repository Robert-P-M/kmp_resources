package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryImpl
import dev.robdoes.kmpresources.domain.model.XmlResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class KmpResourceWorkspaceService(private val project: Project) {

    private val fileStates = ConcurrentHashMap<String, MutableStateFlow<List<XmlResource>>>()

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val isKmpResourceChanged = events.any { event ->
                        val file = event.file ?: return@any false
                        file.path.contains("/composeResources/values") && file.extension == "xml"
                    }

                    if (isKmpResourceChanged) {
                        fileStates.keys.forEach { url ->
                            val vFile = VirtualFileManager.getInstance().findFileByUrl(url)
                            if (vFile != null && vFile.isValid) {
                                reloadFromDisk(vFile)
                            }
                        }
                    }
                }
            }
        )
    }

    fun getResourceStateFlow(file: VirtualFile): StateFlow<List<XmlResource>> {
        val url = file.url
        if (!fileStates.containsKey(url)) {
            fileStates[url] = MutableStateFlow(emptyList())
            reloadFromDisk(file)
        }
        return fileStates[url]!!.asStateFlow()
    }

    private fun reloadFromDisk(file: VirtualFile) {
        val repository = project.service<XmlResourceRepositoryFactory>().create(file) as XmlResourceRepositoryImpl
        val newResources = repository.parseResourcesFromDisk()
        fileStates[file.url]?.value = newResources
    }
}