import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application)   apply false
    alias(libs.plugins.android.library)       apply false
    alias(libs.plugins.kotlin.android)        apply false
    alias(libs.plugins.kotlin.jvm)            apply false
    alias(libs.plugins.kotlin.kapt)           apply false
    alias(libs.plugins.ksp)                   apply false
    alias(libs.plugins.hilt)                  apply false
    alias(libs.plugins.protobuf)              apply false
    alias(libs.plugins.detekt)                apply true
    alias(libs.plugins.ktlint)                apply true
    alias(libs.plugins.dokka)                 apply false
}

// ── Detekt ────────────────────────────────────────────────────────────────────
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("detekt.yml"))
    baseline.set(layout.buildDirectory.file("detekt-baseline.xml"))
    source.setFrom(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude("**/build/**", "**/generated/**")
        }
    )
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
    }
    jvmTarget = "17"
}

// ── ktlint ────────────────────────────────────────────────────────────────────
ktlint {
    version.set("1.3.1")
    android.set(true)
    outputToConsole.set(true)
    filter {
        exclude("**/generated/**")
        include("**/*.kt")
    }
}

// ── Shared Android config applied to all library/app subprojects ──────────────
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
}
