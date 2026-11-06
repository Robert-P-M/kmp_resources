// Root project: no code of its own. Shared configuration lives in build-logic convention plugins.
plugins {
    alias(libs.plugins.intelliJPlatform) apply false
    alias(libs.plugins.changelog) apply false
    alias(libs.plugins.qodana) apply false
}

tasks.wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
}
