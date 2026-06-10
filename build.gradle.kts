plugins {
    base
    id("com.github.ben-manes.versions") version "0.54.0"
}

val defaultVersion = property("version").toString()
val resolvedVersion = if (System.getenv("GITHUB_REF_TYPE") == "tag") {
    System.getenv("GITHUB_REF_NAME")
        ?.takeIf { it.startsWith("v") }
        ?.removePrefix("v")
        ?: defaultVersion
} else {
    defaultVersion
}

allprojects {
    group = "be.theking90000"
    version = resolvedVersion

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21

        withSourcesJar()
        withJavadocJar()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])

                pom {
                    name = project.name
                    description = "Dependency injection utilities for Java."
                    url = "https://github.com/theking90000/scope"

                    licenses {
                        license {
                            name = "MIT License"
                            url = "https://opensource.org/licenses/MIT"
                        }
                    }

                    developers {
                        developer {
                            id = "theking90000"
                            name = "theking90000"
                        }
                    }

                    scm {
                        connection = "scm:git:git://github.com/theking90000/scope.git"
                        developerConnection = "scm:git:ssh://github.com/theking90000/scope.git"
                        url = "https://github.com/theking90000/scope"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/theking90000/scope")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
