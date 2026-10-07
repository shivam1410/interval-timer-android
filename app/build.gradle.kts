plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val versionNameProp = providers.gradleProperty("VERSION_NAME").get()
// 1.2.3 -> 10203
val versionCodeProp = versionNameProp.split(".").map(String::toInt).let { (a, b, c) -> a * 10000 + b * 100 + c }

android {
    namespace = "com.shivam1410.intervaltimer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.shivam1410.intervaltimer"
        minSdk = 36 // Pixel 10 ships Android 16 (API 36); no compat paths below it.
        targetSdk = 36
        versionCode = versionCodeProp
        versionName = versionNameProp
    }

    // Release keystore lives outside the repo; credentials come from ~/.gradle/gradle.properties.
    val storeFilePath = providers.gradleProperty("IT_STORE_FILE").orNull
    signingConfigs {
        if (storeFilePath != null) create("release") {
            storeFile = file(storeFilePath)
            storePassword = providers.gradleProperty("IT_STORE_PASSWORD").get()
            keyAlias = providers.gradleProperty("IT_KEY_ALIAS").get()
            keyPassword = providers.gradleProperty("IT_KEY_PASSWORD").get()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/**" }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
