plugins {
    kotlin("jvm") version "2.1.0" apply false
}

allprojects {
    group = "dev.viaduct"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "isolated"
                    url = uri(
                        providers.gradleProperty("isolatedRepository")
                            .orElse(rootProject.layout.buildDirectory.dir("repository").map {
                                it.asFile.absolutePath
                            })
                            .get()
                    )
                }
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
