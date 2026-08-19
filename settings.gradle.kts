pluginManagement {
    val viaductVersion: String by settings

    repositories {
        mavenLocal()
        if (viaductVersion.endsWith("-SNAPSHOT")) {
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val viaductVersion: String by settings

    repositories {
        mavenLocal()
        if (viaductVersion.endsWith("-SNAPSHOT")) {
            maven("https://central.sonatype.com/repository/maven-snapshots/")
        }
        mavenCentral()
    }
}

rootProject.name = "viaduct-graphql-persistence"

include(
    ":schema-model-core",
    ":hibernate-codegen",
    ":postgresql-overlay",
    ":pg-graphql-overlay",
    ":pg-graphql-translation",
    ":runtime",
    ":liquibase-hibernate-integration",
    ":gradle-plugin",
    ":test-fixtures",
)
