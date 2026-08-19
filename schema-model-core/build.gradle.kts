plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

val viaductVersion: String by project

dependencies {
    api("com.airbnb.viaduct:buildtime:$viaductVersion")
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
