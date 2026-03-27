package dev.robdoes.kmpresources.core.infrastructure.coroutines

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

@Service(Service.Level.PROJECT)
class KmpProjectScopeService(val coroutineScope: CoroutineScope)