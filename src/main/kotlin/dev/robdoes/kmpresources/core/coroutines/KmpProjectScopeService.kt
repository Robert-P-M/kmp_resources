package dev.robdoes.kmpresources.core.coroutines

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class KmpProjectScopeService(val coroutineScope: CoroutineScope)