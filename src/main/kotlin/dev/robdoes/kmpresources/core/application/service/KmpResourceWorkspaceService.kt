package dev.robdoes.kmpresources.core.application.service

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
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

/**
 * Service responsible for managing and monitoring XML resource files within the `composeResources/values` directory
 * in a project. It provides functionality for observing, reloading, and maintaining the state of these resources.
 *
 * This service listens for changes in the virtual file system and updates its internal state accordingly to ensure
 * that the resource data reflects the most recent modifications to the XML files.
 *
 * @constructor Initializes the service with the specified project and sets up file change listeners to monitor
 * updates to XML resource files.
 *
 * @param project The current IntelliJ IDEA project instance for which the service is created.
 */
@Service(Service.Level.PROJECT)
internal class KmpResourceWorkspaceService(private val project: Project) {

    private val fileStates = ConcurrentHashMap<String, MutableStateFlow<List<XmlResource>>>()
    private val scope = project.service<KmpProjectScopeService>().coroutineScope

    init {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {

                    var needsReload = false
                    for (event in events) {
                        val path = event.path
                        if (path.contains("/composeResources/values") && path.endsWith(".xml")) {
                            if (event is VFileDeleteEvent) {
                                fileStates.remove(event.file.url)
                            } else {
                                needsReload = true
                            }
                        }
                    }

                    if (needsReload) {
                        scope.launch(Dispatchers.Default) {
                            fileStates.keys.toList().forEach { url ->
                                val vFile = VirtualFileManager.getInstance().findFileByUrl(url)
                                if (vFile != null && vFile.isValid) {
                                    reloadFromDisk(vFile)
                                } else {
                                    fileStates.remove(url)
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    /**
     * Retrieves a [StateFlow] containing a list of [XmlResource]s associated with the given [VirtualFile].
     * If the resource state for the given file does not already exist, initializes it and triggers a background
     * task to load the resources from disk.
     *
     * @param file The [VirtualFile] for which the resource state flow is being requested.
     * @return A [StateFlow] emitting the list of [XmlResource]s associated with the specified virtual file.
     */
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

    /**
     * Forces a reload of the resources associated with the given virtual file.
     * This method triggers the process of reloading resource data from disk
     * and updates the internal state to reflect the latest resource information.
     *
     * @param file The virtual file whose associated resources need to be reloaded.
     */
    suspend fun forceReload(file: VirtualFile) {
        reloadFromDisk(file)
    }

    /**
     * Reloads the XML resource data from disk for the given virtual file and updates the internal state
     * with the newly parsed resources.
     *
     * @param file The virtual file whose XML resources need to be reloaded from disk.
     */
    private suspend fun reloadFromDisk(file: VirtualFile) {
        val repository = project.service<XmlResourceRepositoryFactory>().create(file) as XmlResourceRepositoryImpl
        val newResources = repository.parseResourcesFromDisk()
        fileStates[file.url]?.value = newResources
    }
}