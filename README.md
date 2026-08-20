# Viaduct GraphQL Persistence

## Overview

Viaduct GraphQL Persistence lets a Viaduct application's GraphQL schema define its data model.
Types, fields, and relationships are written once in GraphQL instead of being maintained
separately in GraphQL, Kotlin, and database mapping code.

The project has two parts:

- The **Gradle plugin** reads the assembled GraphQL schema and generates the database model,
  Kotlin persistence classes, and PostgreSQL integration files.
- The **runtime library** sends Viaduct's selected fields to `pg_graphql` and converts the response
  back into Viaduct result types.

The normal workflow is:

1. Describe the model in GraphQL.
2. Generate and review the proposed database changes.
3. Apply the approved changes through the application's migration system.
4. Use the runtime library to load persisted GraphQL types.

Schema-first does not mean that builds automatically modify a database. Generated SQL is review
input; the application remains responsible for committing and applying migrations.

## Getting Started

### 1. Add the repository

Make the plugin and libraries available to Gradle:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        maven("https://viaduct-dev.github.io/viaduct-graphql-persistence/")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://viaduct-dev.github.io/viaduct-graphql-persistence/")
        mavenCentral()
    }
}
```

### 2. Apply the plugin and runtime

Add the persistence plugin to the Viaduct application and choose a package for generated code:

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.airbnb.viaduct.application-gradle-plugin") version "<viaduct-version>"
    id("dev.viaduct.graphql-persistence") version "0.1.0-SNAPSHOT"
}

dependencies {
    implementation("dev.viaduct:runtime:0.1.0-SNAPSHOT")
}

viaductPersistence {
    packageName.set("com.example.persistence.generated")
}
```

### 3. Define the data model

Write ordinary Viaduct GraphQL types. An object with an `id: ID` field is persistent by default,
and object references describe relationships:

```graphql
type Group {
  id: ID!
  name: String!
  members: [GroupMember!]!
}

type GroupMember {
  id: ID!
  group: Group!
  displayName: String!
}
```

Put types backed by another service, or types that should not become database tables, in files
ending with `.notable.graphqls`.

### 4. Generate the database model

Run:

```bash
./gradlew buildViaductEffectiveModel
```

The generated review files are written under:

```text
build/generated/viaduct-effective-model/META-INF/
```

Start with `postgresql-migration.sql` for relational changes and
`pg-graphql-metadata.sql` for the GraphQL-facing database metadata.

### 5. Review and apply a migration

Review the generated SQL, adapt it to the application's existing schema, and commit the approved
changes to the application's normal migration system. For an existing database,
`hibernateSchemaDiff` can compare the generated model with that database.

The plugin never applies these changes automatically.

### 6. Configure the runtime

Create a `SubtreeClient` with the application's HTTP client, `pg_graphql` endpoint, and
request-specific authentication headers:

```kotlin
val subtreeClient = SubtreeClient(
    httpClient = httpClient,
    endpoint = "$supabaseUrl/graphql/v1",
    requestHeaders = SubtreeRequestHeaders { context ->
        mapOf(
            "Authorization" to "Bearer ${accessTokenFor(context)}",
            "apikey" to supabaseAnonKey,
        )
    },
)
```

Inject this client into Viaduct resolvers that load persistent types. See
[Use pg_graphql as a Subtree Backend](#use-pg_graphql-as-a-subtree-backend) for resolver examples
and [Create or Update a Database](#create-or-update-a-database) for the complete migration
workflow.

## Requirements

- Java 21
- Kotlin/JVM
- A Viaduct application that provides `assembleViaductCentralSchema`
- PostgreSQL when applying the generated SQL
- `pgcrypto` and `pg_graphql` for the complete PostgreSQL/GraphQL overlay

The current artifact version is `0.1.0-SNAPSHOT`.

## Published Artifacts

All libraries use the `dev.viaduct` Maven group:

| Coordinate | Purpose |
| --- | --- |
| `dev.viaduct:runtime` | Viaduct subtree runtime and pg_graphql client |
| `dev.viaduct:pg-graphql-translation` | Transport-neutral selection translation |
| `dev.viaduct:schema-model-core` | Provider-neutral persistence model |
| `dev.viaduct:hibernate-codegen` | Hibernate entity and mapping generation |
| `dev.viaduct:postgresql-overlay` | PostgreSQL persistence overlay |
| `dev.viaduct:pg-graphql-overlay` | pg_graphql naming and relationship overlay |
| `dev.viaduct:liquibase-hibernate-integration` | Liquibase reference database |

The Gradle plugin ID is `dev.viaduct.graphql-persistence`.

## GraphQL Conventions

By default, every object type in an ordinary `*.graphqls` file that has an `id: ID` field is
persistent.

```graphql
type Group {
  id: ID!
  name: String!
  members: [GroupMember!]!
}

type GroupMember {
  id: ID!
  group: Group!
  person: Person!
}

type Person {
  id: ID!
  displayName: String
}
```

This model produces three entity tables. `GroupMember.group` and `GroupMember.person` become
foreign keys. `Group.members` uses the foreign key implied by `GroupMember.group`.

Use object references for relationships. A scalar field such as `groupId` must not shadow a
`group: Group` relationship.

Relationships do not need to be bidirectional. The generated mappings preserve the authored
relationship names used by Viaduct:

- An object reference becomes a foreign key.
- A list with a matching back-reference on the target uses the target foreign key.
- A mutual list relationship uses one deterministic join table.
- A unidirectional list without a matching back-reference uses its own join table.
- Multiple unidirectional lists to the same target use separate join tables.
- Join-table relationships are exposed through generated pg_graphql computed functions.

Join tables are created in the non-exposed `viaduct_internal` schema by default. Self-referential
relationships use distinct owner and target columns. Override the internal schema when needed:

```kotlin
viaductPersistence {
    associationSchemaName.set("application_internal")
}
```

If an existing database intentionally stores a unidirectional collection as a foreign key on the
target table, configure that storage detail without changing GraphQL:

```kotlin
viaductPersistence {
    unidirectionalTargetForeignKeyFields.add("Group.members")
}
```

The raw pg_graphql schema is an internal transport surface, not the authored public API. PostgreSQL
columns required for filtering and pg_graphql's automatic inverse foreign-key relationships may
also be present there. Viaduct remains the public schema and controls which fields clients can use.

Supported scalar mappings are:

| GraphQL | Kotlin/Hibernate |
| --- | --- |
| `ID` | `UUID` |
| `String` | `String` |
| `Date` | `LocalDate` |
| `DateTime` | `OffsetDateTime` |
| `Time` | `LocalTime` |
| `Boolean` | `Boolean` |
| `Byte`, `Short`, `Int`, `Long` | Matching integer type |
| `Float`, `Double` | `Double` |
| GraphQL enum | Generated Kotlin enum |
| One-dimensional scalar list | PostgreSQL array |

Resolver-backed fields that do not form relationships between included persistent types are not
persisted. A type reachable from a `@subtree` root cannot contain a transitively reachable
`@resolver` field because pg_graphql must resolve the complete subtree.

### Excluding Types

Put externally backed or resolver-only types in a file ending in `.notable.graphqls`:

```text
src/main/viaduct/schema/GitHub.notable.graphqls
```

Definitions in notable files are excluded from persistence discovery. A notable file cannot
contain GraphQL extensions, and an ordinary file cannot redefine or extend a type defined in a
notable file. These cases fail generation instead of producing a partial database model.

To use an explicit allowlist instead of discovery:

```kotlin
viaductPersistence {
    includedTypeNames.set(listOf("Group", "GroupMember", "Person"))
}
```

## Tasks

| Task | Purpose |
| --- | --- |
| `validateViaductPersistenceSchema` | Validate subtree and persistence constraints |
| `generateViaductPersistenceModel` | Generate plain entities, `orm.xml`, and semantic metadata |
| `buildViaductEffectiveModel` | Compile the model through Hibernate and generate database overlays |
| `hibernateSchemaSnapshot` | Write a reviewable Liquibase JSON snapshot |
| `hibernateSchemaDiff` | Compare the generated model with a PostgreSQL database |

Generate the effective model:

```bash
./gradlew buildViaductEffectiveModel
```

The main generated outputs are:

```text
build/generated/viaduct-persistence/
  kotlin/...
  resources/META-INF/orm.xml
  resources/META-INF/persistence.xml
  resources/META-INF/viaduct-persistence-model.tsv

build/generated/viaduct-effective-model/META-INF/
  hibernate-metadata-fingerprint.tsv
  persistent-tables.txt
  pg-graphql.sql
  pg-graphql-metadata.sql
  pg-graphql-overlay.sql
  postgresql-migration.sql
  postgresql-prerequisites.sql
  postgresql-repeatable.sql
  viaduct-effective-model.tsv
  viaduct-hibernate-reference.tsv
```

The effective-model directory is packaged into the application JAR.

## Use pg_graphql as a Subtree Backend

`buildViaductEffectiveModel` produces repeatable SQL that maps the generated PostgreSQL schema to
the authored GraphQL names. Apply this file after the relational tables and constraints exist:

```text
build/generated/viaduct-effective-model/META-INF/pg-graphql-metadata.sql
```

`pg-graphql.sql` remains a complete convenience bundle for creating a fresh schema. Do not use the
complete bundle as a repeatable production migration; review `postgresql-migration.sql` as
migration input and use `pg-graphql-metadata.sql` for repeatable metadata.

For Supabase, send GraphQL requests to:

```text
https://<project>.supabase.co/graphql/v1
```

Requests normally include both the caller's JWT and the Supabase API key:

```http
Authorization: Bearer <access-token>
apikey: <publishable-or-anon-key>
Content-Type: application/json
```

The overlay enables row-level security but does not invent authorization policies. Define and
migrate the PostgreSQL RLS policies required by the application.

Viaduct subtree selections and pg_graphql use different collection shapes:

```graphql
# Viaduct
fragment Main on GroupCollection {
  nodes { id name }
}

# pg_graphql equivalent
fragment Main on GroupConnection {
  edges { node { id name } }
}
```

Add the runtime library:

```kotlin
dependencies {
    implementation("dev.viaduct:runtime:0.1.0-SNAPSHOT")
}
```

Configure the endpoint and provider-specific headers. Header resolution runs for every request, so
credentials can come from the current execution context and are not frozen into CRaC checkpoints:

```kotlin
import dev.viaduct.persistence.runtime.SubtreeClient
import dev.viaduct.persistence.runtime.SubtreeRequestHeaders

val subtreeClient = SubtreeClient(
    httpClient = httpClient,
    endpoint = "$supabaseUrl/graphql/v1",
    requestHeaders = SubtreeRequestHeaders { context ->
        mapOf(
            "Authorization" to "Bearer ${accessTokenFor(context)}",
            "apikey" to supabaseAnonKey,
        )
    },
)
```

Node resolvers can then hydrate their owned selections from a filtered pg_graphql collection:

```kotlin
return subtreeClient.fetchByUuid(
    ctx = ctx,
    collectionField = "groupCollection",
    id = ctx.id.internalID,
    ownedSelections = ctx.ownedSelections(),
    requestedSelections = ctx.selections(),
)
```

The runtime also exposes `fetch` for an explicit `Subtree`, `fetchNode` when requested node
references must be attached, and `fetchUuidIds` for collection resolvers that return node
references.

The plugin packages `META-INF/pg-graphql-translation-schema.tsv`, which the runtime loads from the
application classpath. Translation rewrites only actual Viaduct collection fields and marks
generated edge selections with an internal alias. Nested collections are restored recursively
without changing domain fields named `nodes` or `edges`.

The lower-level `pg-graphql-translation` artifact remains available to consumers that need custom
transport. The runtime owns GraphQL request construction, upstream error handling, response-shape
restoration, Viaduct GRT mapping, and node-reference hydration. The application still owns the
`HttpClient` lifecycle, endpoint, and authentication policy.

Every `@subtree` type is validated during generation. A transitively reachable `@resolver` field
is rejected because pg_graphql cannot resolve that field from the database. Types or fields
backed by external services should remain outside that subtree, usually in a
`.notable.graphqls` file.

## Create or Update a Database

The plugin does not apply migrations. A typical workflow is:

1. Change the GraphQL schema.
2. Run `buildViaductEffectiveModel`.
3. Run `hibernateSchemaDiff` against the current database.
4. Review and edit the generated SQL.
5. Commit the approved migration to the application's migration system.
6. Review `META-INF/postgresql-migration.sql` and include the required changes in the migration.
7. Apply `META-INF/pg-graphql-metadata.sql` after the relational schema exists.

Configure the comparison database without committing credentials:

```kotlin
viaductPersistence {
    schemaDiffUrl.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_URL")
    )
    schemaDiffUser.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_USER")
    )
    schemaDiffPassword.set(
        providers.environmentVariable("SCHEMA_DIFF_DATABASE_PASSWORD")
    )
}
```

Then run:

```bash
./gradlew hibernateSchemaDiff
```

The review SQL is written to:

```text
build/schema-diff/hibernate-review.postgresql.sql
```

Potentially destructive changes are separated into:

```text
build/schema-diff/hibernate-destructive-review.postgresql.sql
```

The normal review file excludes drops, empty-comment changes, and duplicate changes. Liquibase
cannot reliably distinguish a rename from a drop plus add, so removals, renames, data backfills,
and database-owned constraints remain explicit manual migration work.

The combined `pg-graphql.sql` overlay:

- Creates self-contained generated global ID columns for supported Viaduct `Node` subtree types.
- Enables row-level security on generated entity tables.
- Preserves authored GraphQL type and relationship names with pg_graphql comments.
- Enforces non-null scalar-array elements.
- Creates computed relationship functions for join tables.

## Customize Hibernate

The defaults use:

- `ViaductImplicitNamingStrategy`
- `ViaductPhysicalNamingStrategy`
- No metadata customizers

The physical strategy pluralizes table names, converts columns to snake case, and maps the
generated internal ID to `_uuid_id`.

Supply compiled application classes by name:

```kotlin
viaductPersistence {
    implicitNamingStrategyClassName.set(
        "com.example.persistence.CustomImplicitNamingStrategy"
    )
    physicalNamingStrategyClassName.set(
        "com.example.persistence.CustomPhysicalNamingStrategy"
    )
    metadataCustomizerClassNames.add(
        "com.example.persistence.CustomMetadataCustomizer"
    )
}
```

A metadata customizer implements:

```kotlin
import dev.viaduct.persistence.hibernate.HibernateMetadataCustomizer
import org.hibernate.boot.MetadataBuilder

class CustomMetadataCustomizer : HibernateMetadataCustomizer {
    override fun customize(metadataBuilder: MetadataBuilder) {
        // Apply additional Hibernate metadata configuration.
    }
}
```

For complete control, replace the generated mapping:

```kotlin
viaductPersistence {
    replacementOrmXml.set(layout.projectDirectory.file("config/orm.xml"))
}
```

`replacementOrmXml` is a complete replacement, not a merge. The effective-model task validates
that it still represents the GraphQL fields, relationship targets, and nullability. Complete
replacement is supported but is not the recommended default.

## Liquibase Database Driver

The `liquibase-hibernate-integration` module registers a Liquibase reference database with this
URL form:

```text
hibernate:viaduct:/absolute/path/to/viaduct-hibernate-reference.tsv
```

The driver reads the generated manifest, creates an isolated classloader from its recorded
classpath, applies the configured Hibernate naming strategies and metadata customizers, and
returns the same metadata used by `buildViaductEffectiveModel`.

Add the driver when using it outside the plugin tasks:

```kotlin
dependencies {
    implementation(
        "dev.viaduct:liquibase-hibernate-integration:0.1.0-SNAPSHOT"
    )
    implementation("org.liquibase:liquibase-core:5.0.3")
    implementation("org.liquibase.ext:liquibase-hibernate7:5.0.3")
}
```

Programmatic usage:

```kotlin
import java.io.File
import liquibase.database.DatabaseFactory
import liquibase.resource.ClassLoaderResourceAccessor
import dev.viaduct.persistence.liquibase.ViaductHibernateDatabase

val manifest = File(
    "build/generated/viaduct-effective-model/" +
        "META-INF/viaduct-hibernate-reference.tsv"
)
val accessor = ClassLoaderResourceAccessor(
    ViaductHibernateDatabase::class.java.classLoader
)
val database = DatabaseFactory.getInstance().openDatabase(
    ViaductHibernateDatabase.referenceUrl(manifest),
    null,
    null,
    null,
    accessor,
)

try {
    // Use as a Liquibase snapshot or diff reference database.
} finally {
    database.close()
    accessor.close()
}
```

Equivalent Liquibase CLI arguments are:

```bash
liquibase \
  --reference-url=hibernate:viaduct:/absolute/path/to/viaduct-hibernate-reference.tsv \
  --url=jdbc:postgresql://127.0.0.1:5432/application \
  --username=postgres \
  --password=postgres \
  diff-changelog \
  --changelog-file=build/schema-diff/review.sql
```

The CLI classpath must contain `liquibase-hibernate-integration` and its transitive dependencies.
The manifest paths are absolute and build-specific; regenerate the effective model after moving
or rebuilding the application.

This driver is a Liquibase reference database, not a JDBC driver and not an application runtime
ORM. Applications using pg_graphql do not need Hibernate in their production runtime. Tests or
tools that explicitly boot the generated persistence unit should add `hibernate-codegen` and a
PostgreSQL JDBC driver to their own test/tool configuration.

```kotlin
dependencies {
    testImplementation(
        "dev.viaduct:hibernate-codegen:0.1.0-SNAPSHOT"
    )
    testImplementation("org.postgresql:postgresql:42.7.5")
}
```

## Local Development

Build and test all modules:

```bash
./gradlew build
```

Publish every artifact to an isolated local Maven repository:

```bash
./gradlew publish \
  -PisolatedRepository=/tmp/viaduct-persistence-repository
```

Use that path in a consumer's `pluginManagement` and `dependencyResolutionManagement`
repositories.

### Release Signing

Release publications are signed with an in-memory PGP key. Supply the credentials as Gradle
properties:

- `signingKeyId`: the signing key ID
- `signingKey`: the ASCII-armored private key
- `signingPassword`: the private key passphrase

In GitHub Actions, expose them as `ORG_GRADLE_PROJECT_signingKeyId`,
`ORG_GRADLE_PROJECT_signingKey`, and `ORG_GRADLE_PROJECT_signingPassword`. Snapshot publications
do not require signing. Publishing a non-snapshot version to a Maven repository fails when signing
credentials are missing.

Pushing a `vX.Y.Z` tag, or manually running the **Build signed artifacts** workflow with a version,
builds and tests every public module, verifies every Maven artifact has a detached signature, and
uploads the signed Maven repository as a GitHub Actions artifact. Manual snapshot builds are
supported for validation. A non-snapshot build fails if any generated POM references a snapshot
dependency.

## Modules

| Module | Responsibility |
| --- | --- |
| `schema-model-core` | Provider-neutral GraphQL persistence model and inclusion policy |
| `hibernate-codegen` | Plain entity/JPA XML generation and effective Hibernate metadata |
| `postgresql-overlay` | PostgreSQL generated-column, RLS, and array-integrity SQL |
| `pg-graphql-overlay` | pg_graphql naming comments and relationship functions |
| `pg-graphql-translation` | Transport-neutral operation and response-shape translation |
| `runtime` | Subtree transport, Viaduct GRT mapping, and node-reference hydration |
| `liquibase-hibernate-integration` | `hibernate:viaduct:` Liquibase reference database |
| `gradle-plugin` | Generation, compilation, overlay, snapshot, and diff orchestration |
| `test-fixtures` | Small synthetic fixtures used only by this repository |

Application schemas remain external compatibility consumers and are not copied into this
repository.

## License

Viaduct GraphQL Persistence is licensed under the
[Apache License, Version 2.0](LICENSE), matching Viaduct.
