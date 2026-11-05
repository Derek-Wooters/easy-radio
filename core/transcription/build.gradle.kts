plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.easyradio.core.transcription"
    compileSdk = 37
    ndkVersion = "27.1.12297006"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a only for this first pass -- matches the one device (Pixel 6) this
            // feature has been validated on; revisit multi-ABI once Phase 1 is proven out.
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_PLATFORM=android-26"
                // AGP maps the Gradle "debug" variant straight to CMake's Debug build type,
                // which for Clang means -g with NO -O flag at all (confirmed via
                // compile_commands.json) -- i.e. completely unoptimized ggml/whisper.cpp.
                // Debugging the *native* numeric core is rarely useful and the slowdown is
                // severe (measured well under 0.3x realtime), so force real optimization
                // even in debug builds; RelWithDebInfo keeps -g for crash symbolication.
                arguments += "-DCMAKE_BUILD_TYPE=RelWithDebInfo"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // WhisperModelProvider.modelFile() opens this asset via AssetManager.openFd(), which
    // requires it to be stored uncompressed in the APK -- :app already sets this for its own
    // build; the androidTest APK packages this module's assets directly and needs it too.
    androidResources {
        noCompress += "bin"
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.truth)
}
