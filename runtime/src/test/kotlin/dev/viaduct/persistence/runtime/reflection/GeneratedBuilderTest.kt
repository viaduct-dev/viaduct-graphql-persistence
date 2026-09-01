package dev.viaduct.persistence.runtime.reflection

import graphql.schema.GraphQLObjectType
import io.mockk.mockk
import viaduct.api.internal.InternalContext
import viaduct.engine.api.EngineObjectData
import kotlin.test.Test
import kotlin.test.assertEquals

class GeneratedBuilderTest {
    @Test
    fun `finds connection builder constructor using assignable generated data type`() {
        val context = mockk<InternalContext>()
        val graphQlType = GraphQLObjectType.newObject().name("FixtureConnection").build()
        val data = mockk<EngineObjectData.Sync>()

        val builder =
            GeneratedBuilder.fromConnection(
                builderClass = SyncConnectionBuilder::class.java,
                context = context,
                graphQlType = graphQlType,
                data = data,
            )

        assertEquals("built", builder.build())
    }
}

private class SyncConnectionBuilder(
    @Suppress("UNUSED_PARAMETER") context: InternalContext,
    @Suppress("UNUSED_PARAMETER") graphQlType: GraphQLObjectType,
    @Suppress("UNUSED_PARAMETER") data: EngineObjectData.Sync,
) {
    private val result = "built"

    fun build(): String = result
}
