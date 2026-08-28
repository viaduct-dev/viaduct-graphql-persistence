package dev.viaduct.persistence.runtime

import viaduct.api.select.OutputSelectionFragment

/** The standard pagination arguments declared by a Viaduct connection field. */
internal data class ConnectionPaginationArguments(
    private val renderedArguments: List<String> = emptyList(),
) {
    fun render(): String =
        renderedArguments.takeUnless(List<String>::isEmpty)?.joinToString(
            prefix = "(",
            postfix = ")",
            separator = ",",
        ) ?: ""

    companion object {
        val NONE = ConnectionPaginationArguments()

        fun fromFragment(
            fragment: OutputSelectionFragment,
        ): Map<String, ConnectionPaginationArguments> =
            ConnectionArgumentExtractor.fromFragment(fragment)
    }
}
