/*
 * Convention for pure Kotlin/JVM library modules (e.g. `core`, `gradle-plugin`):
 * explicit API mode, Kotlin stdlib on the classpath and JUnit 5 for tests.
 */
plugins {
    id("kmpres.kotlin-jvm")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
}

dependencies {
    "implementation"(kotlin("stdlib"))
    "testImplementation"(kotlin("test-junit5"))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
