package dev.viaduct.persistence.liquibase.pggraphql

import liquibase.database.Database
import liquibase.diff.ObjectDifferences
import liquibase.diff.compare.CompareControl
import liquibase.diff.compare.DatabaseObjectComparator
import liquibase.diff.compare.DatabaseObjectComparatorChain
import liquibase.structure.DatabaseObject
import liquibase.structure.core.ForeignKey

/** Adds the pg_graphql relationship comment to the standard foreign-key difference set. */
class PgGraphqlCommentComparator : DatabaseObjectComparator {
    override fun getPriority(
        objectType: Class<out DatabaseObject>,
        database: Database,
    ): Int =
        if (ForeignKey::class.java.isAssignableFrom(objectType)) {
            PG_GRAPHQL_COMMENT_PRIORITY
        } else {
            DatabaseObjectComparator.PRIORITY_NONE
        }

    override fun isSameObject(
        databaseObject1: DatabaseObject,
        databaseObject2: DatabaseObject,
        accordingTo: Database,
        chain: DatabaseObjectComparatorChain,
    ): Boolean = chain.isSameObject(databaseObject1, databaseObject2, accordingTo)

    override fun hash(
        databaseObject: DatabaseObject,
        accordingTo: Database,
        chain: DatabaseObjectComparatorChain,
    ): Array<String> = chain.hash(databaseObject, accordingTo)

    override fun findDifferences(
        referenceObject: DatabaseObject,
        comparedObject: DatabaseObject,
        accordingTo: Database,
        compareControl: CompareControl,
        chain: DatabaseObjectComparatorChain,
        exclude: MutableSet<String>,
    ): ObjectDifferences {
        val differences = chain.findDifferences(referenceObject, comparedObject, accordingTo, compareControl, exclude)
        if (referenceObject is ForeignKey && comparedObject is ForeignKey) {
            val referenceComment = referenceObject.getAttribute(PG_GRAPHQL_COMMENT_ATTRIBUTE, String::class.java)
            val comparedComment = comparedObject.getAttribute(PG_GRAPHQL_COMMENT_ATTRIBUTE, String::class.java)
            if (referenceComment != comparedComment) {
                differences.addDifference(PG_GRAPHQL_COMMENT_ATTRIBUTE, referenceComment, comparedComment)
            }
        }
        return differences
    }
}
