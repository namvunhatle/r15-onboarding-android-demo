plugins { id("com.android.application") }

android {
    namespace = "namvunhatle.r15.onboarding.views"
    compileSdk = 37
    defaultConfig {
        applicationId = "namvunhatle.r15.onboarding.views.responsive"
        minSdk = 28
        targetSdk = 37
        versionCode = 5
        versionName = "1.3.7"
    }
    // Review builds are release builds (R8, not debuggable) signed with the debug key: a debuggable build opens ~3× slower.
    buildTypes {
        release {
            isMinifyEnabled = true // resources are not shrunk, as on main
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
}
