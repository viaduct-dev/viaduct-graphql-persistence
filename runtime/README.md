# Runtime

The runtime executes Viaduct-owned db selections against a pg_graphql endpoint. It derives
translation information from the generated Viaduct reflection types at request time; it does not
load a generated translation descriptor.

```kotlin
dependencies {
    implementation("dev.viaduct.persistence:runtime:0.1.0-SNAPSHOT")
}
```

```kotlin
val client = DbClient(
    httpClient = httpClient,
    endpoint = "$postgresGraphqlEndpoint/graphql",
    requestHeaders = DbRequestHeaders { context ->
        mapOf("Authorization" to "Bearer ${accessTokenFor(context)}")
    },
)
```

`DbClient` provides:

- `fetch` for an explicit db root and owned selection set. Shortcut for `fetchJson` + `toGRT`.
- `fetchJson` for the same read, returning the raw pg_graphql JSON without converting it to a GRT.
- `toGRT` (an extension on the `JsonObject` result) to convert JSON — from `fetchJson`, a cache, or
  any other source — into the generated Viaduct value for a typed selection set.
- `fetchNode` for results that also need requested node references.
- `fetchByInternalId`/`fetchByInternalIds` for the common filtered-collection node lookup.
- `fetchUuidIds` for collection resolvers that return Viaduct node references.
- `fetchUuidConnection` for caller-managed `first`/`after` or `last`/`before` pagination.
- `fetchNestedUuidConnections` for one paginated child connection per parent in one request.

Fetching and converting are separate, explicit steps rather than one fused operation: `fetch` is
built from `fetchJson` and `toGRT`, not the other way around. Reach for the two parts directly when
you need the JSON on its own — inspecting a response before conversion, or converting JSON obtained
some other way:

```kotlin
val json = client.fetchJson(ctx, dbRead, selections)
val value = json.toGRT(ctx, selections)
```

The application owns the HTTP client lifecycle, endpoint, and header policy. The runtime owns
query translation, transport execution, GraphQL error propagation, response restoration, GRT
mapping, and node-reference hydration. A generated `ConnectionBuilder` with a compatibility
`nodes` field or an `edges { node }` shape is recognized structurally, so nested connections and
ordinary domain fields named `nodes` remain schema-safe without translation metadata.

When a connection uses a join table, the pg_graphql path resolver uses the real
`<fieldName>Associations` relationship (for example, `membersAssociations`), even when the edge
contains only `node` and `cursor`. Pagination, filters, and ordering are applied to association
rows. Each response row is then unwrapped from `association.node` into the Viaduct edge node while
the remaining association columns become edge fields; a single unidirectional connection uses the
target relationship directly. No edge view or SQL function is required.
