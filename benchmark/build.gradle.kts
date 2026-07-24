plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "net.meshnet.benchmark"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core:crypto"))
    implementation(project(":core:routing"))
    implementation(project(":core:storage"))
    androidTestImplementation(libs.benchmark.macro.junit4)
    androidTestImplementation(libs.junit.jupiter)
}
