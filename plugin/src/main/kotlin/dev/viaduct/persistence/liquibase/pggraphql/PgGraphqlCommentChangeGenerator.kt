package dev.viaduct.persistence.liquibase.pggraphql

import dev.viaduct.persistence.pggraphql.overlay.quoteIdentifier
import liquibase.change.Change
import liquibase.change.core.RawSQLChange
import liquibase.database.Database
import liquibase.diff.ObjectDifferences
import liquibase.diff.compare.CompareControl
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.ChangeGeneratorChain
import liquibase.diff.output.changelog.ChangedObjectChangeGenerator
import liquibase.diff.output.changelog.MissingObjectChangeGenerator
import liquibase.structure.DatabaseObject
import liquibase.structure.core.ForeignKey

/**
 * Emits a `COMMENT ON CONSTRAINT` change whenever a foreign key's pg_graphql comment differs
 * (an existing constraint whose comment drifted) or is being created for the first time (a new
 * constraint that doesn't exist on the comparison database yet).
 */
class PgGraphqlCommentChangeGenerator :
    ChangedObjectChangeGenerator,
    MissingObjectChangeGenerator {
    override fun getPriority(
        objectType: Class<out DatabaseObject>,
        database: Database,
    ): Int =
        if (ForeignKey::class.java.isAssignableFrom(objectType)) {
            PG_GRAPHQL_COMMENT_PRIORITY
        } else {
            liquibase.diff.output.changelog.ChangeGenerator.PRIORITY_NONE
        }

    override fun runAfterTypes(): Array<Class<out DatabaseObject>> = emptyArray()

    override fun runBeforeTypes(): Array<Class<out DatabaseObject>> = emptyArray()

    override fun fixSchema(
        changes: Array<Change>,
        schemaComparisons: Array<CompareControl.SchemaComparison>,
    ): Array<Change> = changes

    override fun fixOutputAsSchema(
        changes: Array<Change>,
        schemaComparisons: Array<CompareControl.SchemaComparison>,
    ): Array<Change> = changes

    override fun fixChanged(
        databaseObject: DatabaseObject,
        differences: ObjectDifferences,
        control: DiffOutputControl,
        referenceDatabase: Database,
        comparisonDatabase: Database,
        chain: ChangeGeneratorChain,
    ): Array<Change> {
        val inherited =
            chain.fixChanged(databaseObject, differences, control, referenceDatabase, comparisonDatabase)
                ?: emptyArray()
        if (databaseObject !is ForeignKey || !differences.isDifferent(PG_GRAPHQL_COMMENT_ATTRIBUTE)) {
            return inherited
        }
        val desiredComment = differences.getDifference(PG_GRAPHQL_COMMENT_ATTRIBUTE).referenceValue as String?
        return inherited + commentChange(databaseObject, desiredComment)
    }

    override fun fixMissing(
        databaseObject: DatabaseObject,
        control: DiffOutputControl,
        referenceDatabase: Database,
        comparisonDatabase: Database,
        chain: ChangeGeneratorChain,
    ): Array<Change> {
        val inherited =
            chain.fixMissing(databaseObject, control, referenceDatabase, comparisonDatabase) ?: emptyArray()
        if (databaseObject !is ForeignKey) return inherited
        val desiredComment = databaseObject.getAttribute(PG_GRAPHQL_COMMENT_ATTRIBUTE, String::class.java)
        return if (desiredComment == null) inherited else inherited + commentChange(databaseObject, desiredComment)
    }

    private fun commentChange(
        foreignKey: ForeignKey,
        desiredComment: String?,
    ): RawSQLChange {
        val schemaName = foreignKey.foreignKeyTable.schema.name
        val tableName = foreignKey.foreignKeyTable.name
        val sql =
            "COMMENT ON CONSTRAINT ${quoteIdentifier(foreignKey.name)} " +
                "ON ${quoteIdentifier(schemaName)}.${quoteIdentifier(tableName)} " +
                "IS ${sqlStringLiteralOrNull(desiredComment)};"
        return RawSQLChange(sql)
    }

    private fun sqlStringLiteralOrNull(value: String?): String {
        if (value == null) return "NULL"
        return "'${value.replace("'", "''")}'"
    }
}
