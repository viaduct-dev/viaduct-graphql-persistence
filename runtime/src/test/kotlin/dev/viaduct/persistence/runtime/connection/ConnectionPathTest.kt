package dev.viaduct.persistence.runtime.connection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionPathTest {
    @Test
    fun `unwraps association rows without changing their connection envelope`() {
        val path =
            ConnectionPath(
                requestFieldName = "membersAssociations",
                associationNodeFieldName = "node",
            )
        val edge =
            Json
                .parseToJsonElement(
                    """{"node":{"node":{"uuidId":"person-1"},"role":"admin"}}""",
                ).jsonObject

        assertEquals(
            "person-1",
            path
                .targetNode(edge)
                ?.get("uuidId")
                ?.jsonPrimitive
                ?.content,
        )
        assertEquals("admin", path.edgeValue(edge, "role")?.jsonPrimitive?.content)
    }
}
