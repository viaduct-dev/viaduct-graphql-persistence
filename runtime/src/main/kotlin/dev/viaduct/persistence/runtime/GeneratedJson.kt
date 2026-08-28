@file:OptIn(viaduct.apiannotations.InternalApi::class)

package dev.viaduct.persistence.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import viaduct.api.context.ExecutionContext
import viaduct.api.mapping.GRTDomain
import viaduct.api.mapping.JsonDomain
import viaduct.api.select.SelectionSet
import viaduct.api.types.CompositeOutput

/** Converts a pg_graphql object into the generated Viaduct value for a typed selection set. */
@Suppress("UNCHECKED_CAST")
internal fun <T : CompositeOutput> JsonObject.toGRT(
    ctx: ExecutionContext,
    selections: SelectionSet<T>,
): T {
    val jsonString = Json.encodeToString(JsonObject.serializer(), this)
    return JsonDomain
        .forSelectionSet(ctx, selections)
        .mapperTo(GRTDomain.forSelectionSet(ctx, selections))(jsonString) as T
}
