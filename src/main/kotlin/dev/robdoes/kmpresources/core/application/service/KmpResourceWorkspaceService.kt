package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiDocumentManager
import dev.robdoes.kmpresources.core.infrastructure.coroutines.KmpProjectScopeService
import dev.robdoes.kmpresources.core.infrastructure.coroutines.awaitSmartMode
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryFactory
import dev.robdoes.kmpresources.data.repository.XmlResourceRepositoryImpl
import dev.robdoes.kmpresources.domain.model.XmlResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class KmpResourceWorkspaceService(private val project: Project) {

    private val fileStates = ConcurrentHashMap<String, MutableStateFlow<List<XmlResource>>>()
    private val scope = project.service<KmpProjectScopeService>().coroutineScope

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val changedKmpFiles = events.mapNotNull { it.file }.filter {
                        it.path.contains("/composeResources/values") && it.extension == "xml"
                    }
                    if (changedKmpFiles.isNotEmpty()) {
                        scope.launch(Dispatchers.Default) {
                            fileStates
                                .keys
                                .forEach { url ->
                                    val vFile = VirtualFileManager.getInstance().findFileByUrl(url)
                                    if (vFile != null && vFile.isValid) {
                                        reloadFromDisk(vFile)
                                    }
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
            scope.launch(Dispatchers.Default) {
                project.awaitSmartMode()

                withContext(Dispatchers.EDT) {
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }

                reloadFromDisk(file)
            }
        }
        return fileStates[url]!!.asStateFlow()
    }

    suspend fun forceReload(file: VirtualFile) {
        reloadFromDisk(file)
    }

    private suspend fun reloadFromDisk(file: VirtualFile) {
        val repository = project.service<XmlResourceRepositoryFactory>().create(file) as XmlResourceRepositoryImpl
        val newResources = repository.parseResourcesFromDisk()
        fileStates[file.url]?.value = newResources
    }
}