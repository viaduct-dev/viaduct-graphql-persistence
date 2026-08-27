@file:OptIn(viaduct.apiannotations.ExperimentalApi::class)

package dev.viaduct.persistence.runtime

import viaduct.api.context.ResolverExecutionContext
import viaduct.api.reflect.Type
import viaduct.api.types.NodeObject
import viaduct.api.types.Query

internal class NodeReferenceResolver {
    @Suppress("UNCHECKED_CAST")
    fun resolve(
        context: ResolverExecutionContext<out Query>,
        type: Type<*>,
        internalId: String,
    ): NodeObject = context.nodeRef(
        context.globalIDFor(type as Type<NodeObject>, internalId)
    )
}
