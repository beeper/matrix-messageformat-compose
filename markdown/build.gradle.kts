plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

group = "com.beeper.android.messageformat"
version = "1.0"

kotlin {

    jvm()

    jvmToolchain(17)

    androidLibrary {
        namespace = "com.beeper.android.messageformat.markdown"
        compileSdk = 37
        minSdk = 24

        withHostTestBuilder {
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                api(libs.jetbrains.markdown)
                api(project(":messageformat"))
                implementation(compose.runtime)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.compose.ui)
            }
        }
    }
}
