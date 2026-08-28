package dev.viaduct.persistence.runtime

import viaduct.api.select.OutputSelectionFragment

/** The standard pagination arguments declared by a Viaduct connection field. */
internal class ConnectionPaginationArguments private constructor(
    private val renderedArguments: List<String>,
) {
    fun render(): String =
        renderedArguments.takeUnless(List<String>::isEmpty)?.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ",",
        ) ?: ""

    companion object {
        fun none() = ConnectionPaginationArguments(emptyList())

        internal fun fromArguments(arguments: List<String>) = ConnectionPaginationArguments(arguments)

        fun fromFragment(fragment: OutputSelectionFragment): Map<String, ConnectionPaginationArguments> =
            ConnectionArgumentExtractor.fromFragment(fragment)
    }
}
