plugins { id("com.android.application") }

android {
    namespace = "namvunhatle.r15.onboarding.sample"
    compileSdk = 37
    defaultConfig {
        applicationId = "namvunhatle.r15.onboarding.sample"
        minSdk = 28
        targetSdk = 37
        versionCode = 167
        versionName = "1.6.7"
    }
    // Review builds are release builds (R8, not debuggable) signed with the debug key: a debuggable build opens ~3× slower.
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":a7onboarding"))
    implementation("androidx.core:core-ktx:1.19.0")
}
