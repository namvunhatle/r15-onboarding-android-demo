plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "namvunhatle.r15.onboarding.compose"
    compileSdk = 37
    defaultConfig {
        applicationId = "namvunhatle.r15.onboarding.compose.responsive"
        minSdk = 28
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.7"
    }
    buildFeatures { compose = true }
    // Review builds are release builds (R8, not debuggable) signed with the debug key: a debuggable build opens ~3× slower.
    buildTypes {
        release {
            isMinifyEnabled = true // no resource shrinking: Compose looks sprites up by name
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
}
