plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    useJUnit()
}
