plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    kotlin("plugin.serialization") version "2.1.0"
}

dependencies {
    implementation("com.graphql-java:graphql-java:22.3")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
}
