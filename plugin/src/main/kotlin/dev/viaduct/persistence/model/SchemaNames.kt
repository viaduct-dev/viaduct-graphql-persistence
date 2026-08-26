package dev.viaduct.persistence.model

private val ACRONYM_NORMALIZATIONS = listOf(
    Regex("GitHub") to "Github",
    Regex("GraphQL") to "Graphql",
)

private val INVARIANT_PLURAL_NOUNS = setOf(
    "news",
    "series",
    "species",
)

fun toSnakeCase(name: String): String {
    var normalized = name
    for ((pattern, replacement) in ACRONYM_NORMALIZATIONS) {
        normalized = normalized.replace(pattern, replacement)
    }
    return normalized
        .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
        .lowercase()
}

fun toTableName(typeName: String): String {
    val snake = toSnakeCase(typeName)
    val noun = snake.substringAfterLast('_')
    return when {
        noun in INVARIANT_PLURAL_NOUNS -> snake
        snake.endsWith("_identity") || snake == "identity" ->
            snake.dropLast("identity".length) + "identities"
        snake.endsWith("icy") ->
            snake.dropLast(1) + "ies"
        snake.endsWith("y") && !snake.endsWith("ay") && !snake.endsWith("ey") &&
            !snake.endsWith("iy") && !snake.endsWith("oy") && !snake.endsWith("uy") ->
            snake.dropLast(1) + "ies"
        snake.endsWith("s") || snake.endsWith("x") || snake.endsWith("z") ->
            snake + "es"
        else -> snake + "s"
    }
}
