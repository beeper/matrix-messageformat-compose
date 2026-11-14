plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.multiplatform.compose)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    id("maven-publish")
}

group = "com.beeper.android.messageformat"
version = "1.0"

kotlin {

    jvm()

    jvmToolchain(17)

    compilerOptions {
        optIn.add("androidx.compose.foundation.ExperimentalFoundationApi")
    }

    androidLibrary {
        namespace = "com.beeper.android.messageformat.composer"
        compileSdk = 37
        minSdk = 24

        withHostTestBuilder {
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                api(project(":markdown"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
                implementation(libs.kermit)
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
