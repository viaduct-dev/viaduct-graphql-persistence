plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    kotlin("plugin.serialization") version "2.1.0"
}

val viaductVersion: String by project

dependencies {
    api(project(":pg-graphql-translation"))
    api("com.airbnb.viaduct:api:$viaductVersion")
    api("io.ktor:ktor-client-core:3.2.0")

    implementation("com.airbnb.viaduct:runtime:$viaductVersion")
    implementation("com.graphql-java:graphql-java:22.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("io.ktor:ktor-client-mock:3.2.0")
    testImplementation("io.mockk:mockk:1.13.16")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
}
