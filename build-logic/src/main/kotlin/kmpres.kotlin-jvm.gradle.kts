/*
 * Base convention for every Kotlin/JVM module of this repository:
 * JDK 21 toolchain, strict compiler settings and Kover coverage.
 *
 * The Kotlin standard library is NOT added automatically (`kotlin.stdlib.default.dependency=false`),
 * because the IDE plugin must use the stdlib bundled with the IDE. Library modules add it explicitly
 * via the `kmpres.kotlin-library` convention.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors.set(providers.gradleProperty("kmpres.warningsAsErrors").map(String::toBoolean).orElse(false))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}
