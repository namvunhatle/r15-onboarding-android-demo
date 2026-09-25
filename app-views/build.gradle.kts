plugins { id("com.android.application") }

android {
    namespace = "namvunhatle.r15.onboarding.views"
    compileSdk = 37
    defaultConfig {
        applicationId = "namvunhatle.r15.onboarding.views.responsive"
        minSdk = 28
        targetSdk = 37
        versionCode = 4
        versionName = "1.3.6"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.core:core-ktx:1.19.0")
}
