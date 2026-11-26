plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "app.bp"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.bp"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // Needed for Compose UI tests under Robolectric.
            isIncludeAndroidResources = true
        }
    }
}

// Release artifact carries the version in its file name:
// bp-tracker-v<versionName>-<buildType>.apk (housekeeping: the raw
// Gradle output is a nameless app-debug.apk).
tasks.register<Copy>("copyVersionedApk") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    // Its own directory: AGP's listing-file task declares the apk/debug
    // directory as its input, so writing alongside app-debug.apk would trip
    // task-output validation.
    into(layout.buildDirectory.dir("outputs/apk/versioned"))
    rename { "bp-tracker-v${android.defaultConfig.versionName}-debug.apk" }
}
// AGP registers the variant tasks in afterEvaluate, so wire lazily.
tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyVersionedApk")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.14")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
