# pg_graphql Translation

This module translates between Viaduct subtree selections and pg_graphql collection responses.
It is transport-neutral and does not depend on a Viaduct runtime or HTTP client.

```kotlin
dependencies {
    implementation(
        "dev.viaduct.persistence:pg-graphql-translation:0.1.0-SNAPSHOT"
    )
}
```

```kotlin
val schema = PgGraphqlTranslationSchema.decode(
    checkNotNull(
        javaClass.classLoader.getResource(PgGraphqlTranslationSchema.RESOURCE)
    ).readText()
)
val pgFragment = PgGraphqlTranslation.translateSelectionDocument(
    viaductFragment,
    schema,
)
val query = PgGraphqlTranslation.buildRootQuery(
    field = "groupCollection",
    arguments = "",
    variableDefinitions = "",
    fragmentDocument = pgFragment,
    singleViaFilteredCollection = false,
)

// POST query to pg_graphql, extract data.groupCollection, then:
val viaductJson =
    PgGraphqlTranslation.restoreViaductResponseShape(pgGraphqlRootFieldJson)
```

Translation changes Viaduct `Collection` fragment types to pg_graphql `Connection` types and
changes `nodes { ... }` to `edges { node { ... } }`. Response restoration performs the inverse
shape conversion recursively. The generated schema descriptor limits rewriting to actual
collection types, so ordinary fields named `nodes` or `edges` are preserved.

See [Use pg_graphql as a Subtree Backend](../README.md#use-pg_graphql-as-a-subtree-backend) for
overlay installation, Supabase headers, filtered single-row lookups, bare lists, and subtree
validation rules.
