plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.travelpins.test"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.travelpins.test"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        manifestPlaceholders["MAPS_API_KEY"] =
            project.findProperty("MAPS_API_KEY")?.toString() ?: ""
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "2.2.20"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:20.0.0")
    implementation("com.google.maps.android:maps-compose:6.12.0")

    // Coil (caricamento immagini da URL)
    implementation("io.coil-kt:coil-compose:2.7.0")
}
