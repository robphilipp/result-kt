plugins {
    kotlin("jvm") version "1.8.0"
    `maven-publish`
    `java-library`
    publishing
    signing
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"
}

group = "com.digitalcipher"
version = "0.0.1-snapshot"

repositories {
    mavenCentral()
}

nexusPublishing {
    repositories {
        sonatype {
            username.set("sonatypeUsername")
            password.set("sonatypePassword")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("resultKt") {
            from(components["kotlin"])

            pom {
                name.set("result-kt")
                description.set("Result class for Kotlin")
                url.set("https://github.com/robphilipp/result-kt")

                licenses {
                    license {
                        name.set("Eclipse Public License - v 2.0")
                        url.set("https://github.com/robphilipp/result-kt/LICENSE")
                    }
                }

                organization {
                    name.set("com.digitalcipher")
                    url.set("https://github.com/robphilipp")
                }

                developers {
                    developer {
                        id.set("rob.philipp")
                        name.set("Rob Philipp")
                        email.set("rob.philipp@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/robphilipp/result-kt.git")
                    developerConnection.set("scm:git:ssh://git@github.com:robphilipp/result-kt.git")
                    url.set("https://github.com/robphilipp/result-kt")
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("https://github.com/robphilipp/result-kt/issues")
                }
            }
        }
    }
}

signing {
    sign(publishing.publications["resultKt"])
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}
