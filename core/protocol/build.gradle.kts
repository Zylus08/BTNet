plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
}

android {
    namespace = "net.meshnet.core.protocol"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Extract libs before protobuf block to avoid Kotlin DSL scope resolution issues
val projectLibs = libs

protobuf {
    protoc {
        artifact = projectLibs.protobuf.protoc.get().toString()
    }

    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").apply {
                    option("lite")
                }
                maybeCreate("kotlin").apply {
                    option("lite")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
