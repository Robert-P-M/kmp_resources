/*
 * KMP Resources Gradle plugin: generates Android / Compose Multiplatform string resources
 * from the central YAML catalog.
 */
plugins {
    id("kmpres.kotlin-library")
    `java-gradle-plugin`
}

group = "dev.robdoes.kmpresources"

dependencies {
    implementation(project(":core"))
}
