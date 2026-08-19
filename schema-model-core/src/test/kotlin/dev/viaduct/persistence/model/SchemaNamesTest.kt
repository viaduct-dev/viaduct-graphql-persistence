package dev.viaduct.persistence.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SchemaNamesTest {
    @Test
    fun `uses stable default table-name inflections`() {
        assertEquals("groups", toTableName("Group"))
        assertEquals("policies", toTableName("Policy"))
        assertEquals("statuses", toTableName("Status"))
        assertEquals("external_identities", toTableName("ExternalIdentity"))
        assertEquals("species", toTableName("Species"))
        assertEquals("news", toTableName("News"))
    }
}
