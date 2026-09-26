plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.vrushabhhirap.shukracharyascurse"
    compileSdk {
        version = release(37)
    }

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.vrushabhhirap.shukracharyascurse"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cFlags("-O2", "-fvisibility=hidden", "-fstack-protector-strong")
                arguments("-DANDROID_STL=none")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
            optimization {
                enable = false
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/README*"
            excludes += "**/*.md"
        }
    }
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:!*.md"
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}