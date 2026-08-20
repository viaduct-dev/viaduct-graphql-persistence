# Liquibase Hibernate Integration

This module provides the `hibernate:viaduct:` Liquibase reference database:

```text
hibernate:viaduct:/absolute/path/to/viaduct-hibernate-reference.tsv
```

Add the driver to the classpath:

```kotlin
dependencies {
    implementation(
        "dev.viaduct:liquibase-hibernate-integration:0.1.0-SNAPSHOT"
    )
}
```

The manifest is produced by the Gradle plugin's `buildViaductEffectiveModel` task. The driver
loads the recorded application classpath and reconstructs the exact Hibernate metadata used to
generate the PostgreSQL overlays.

Use it as a Liquibase snapshot or diff reference:

```bash
liquibase \
  --reference-url=hibernate:viaduct:/absolute/path/to/viaduct-hibernate-reference.tsv \
  --url=jdbc:postgresql://127.0.0.1:5432/application \
  --username=postgres \
  --password=postgres \
  diff-changelog \
  --changelog-file=build/schema-diff/review.sql
```

When using the Gradle plugin, `hibernateSchemaDiff` writes conservative additions and alterations
to `build/schema-diff/hibernate-review.postgresql.sql`. Drops and other destructive operations are
placed in `hibernate-destructive-review.postgresql.sql` for explicit review. This prevents
database-owned constraints and defaults that are absent from GraphQL/Hibernate metadata from being
silently removed.

This is a Liquibase database implementation, not a JDBC driver or a production runtime ORM.
The CLI classpath must contain this module and its transitive dependencies.

See the repository [README](../README.md#liquibase-database-driver) for programmatic usage and
manifest portability constraints.
