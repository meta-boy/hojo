plugins {
    // Apply the application plugin (from [plugins] section)
    alias(libs.plugins.android.application)
    // Apply the Kotlin Android plugin (from [plugins] section)
    alias(libs.plugins.kotlin.android)
    // Apply the Kotlin Compose compiler plugin (from [plugins] section)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("kotlin-kapt")
}

android {
    // Set your project's namespace
    namespace = "wtf.anurag.hojo"

    // Use the maximum SDK version
    compileSdk = 34 // Use a fixed, stable number or a version variable if defined

    defaultConfig {
        applicationId = "wtf.anurag.hojo"
        minSdk = 26
        targetSdk = 34 // Example target SDK
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions { jvmTarget = "1.8" }

    // Enable the Compose build feature
    buildFeatures { compose = true }
}

dependencies {
    // Core Android and Kotlin KTX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // --- Jetpack Compose ---

    // Use the Compose BOM to manage versions for all Compose libraries
    implementation(platform(libs.androidx.compose.bom))

    // Main Compose UI dependencies
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics) // Contains ColorMatrix
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // --- Hilt ---
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // --- Third-party Libraries ---
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.android.image.cropper)
    implementation(libs.epublib) {
        exclude(group = "xmlpull", module = "xmlpull")
        exclude(group = "net.sf.kxml", module = "kxml2")
    }
    implementation("net.sf.kxml:kxml2:2.3.0") {
        exclude(group = "xmlpull", module = "xmlpull")
    }

    // --- Testing Dependencies ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Compose Testing (requires the BOM platform again for consistent versions)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Debugging and Tooling
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}


android {
    packaging {
        resources {
            excludes += "org/xmlpull/v1/**"
        }
    }
    configurations.all {
        exclude(group = "xmlpull", module = "xmlpull")
    }
}
