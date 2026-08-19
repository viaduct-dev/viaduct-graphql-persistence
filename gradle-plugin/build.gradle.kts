plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    `maven-publish`
}

val viaductVersion: String by project

dependencies {
    implementation(project(":schema-model-core"))
    implementation(project(":hibernate-codegen"))
    implementation(project(":postgresql-overlay"))
    implementation(project(":pg-graphql-overlay"))
    implementation(project(":pg-graphql-translation"))
    implementation(project(":liquibase-hibernate-integration"))
    implementation("com.airbnb.viaduct:buildtime:$viaductVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    runtimeOnly("org.liquibase:liquibase-core:5.0.3")
    runtimeOnly("org.liquibase.ext:liquibase-hibernate7:5.0.3")
    runtimeOnly("info.picocli:picocli:4.7.7")
    runtimeOnly("org.postgresql:postgresql:42.7.5")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

gradlePlugin {
    plugins {
        create("viaductPersistence") {
            id = "dev.viaduct.graphql-persistence"
            implementationClass =
                "dev.viaduct.persistence.gradle.ViaductPersistencePlugin"
            displayName = "Viaduct GraphQL Persistence"
            description =
                "Generates Hibernate metadata and database review artifacts from Viaduct GraphQL"
        }
    }
}
