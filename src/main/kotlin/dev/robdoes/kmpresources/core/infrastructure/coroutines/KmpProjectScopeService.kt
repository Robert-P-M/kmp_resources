package dev.robdoes.kmpresources.core.infrastructure.coroutines

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope

/**
 * A service scoped to the project level, designed to manage a [CoroutineScope] within the context of an IntelliJ IDEA plugin.
 *
 * This service is typically used for coroutine management tied to a specific project lifecycle. It ensures that the
 * coroutines launched within this scope are bound to the project and can be cancelled or managed as part of the
 * project's lifecycle events.
 *
 * @constructor Creates a new instance of [KmpProjectScopeService] with the provided [CoroutineScope].
 * @param coroutineScope The [CoroutineScope] associated with this service, used to manage coroutines at the project level.
 */
@Service(Service.Level.PROJECT)
internal class KmpProjectScopeService(val coroutineScope: CoroutineScope)