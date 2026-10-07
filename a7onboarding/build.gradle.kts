// The A7 onboarding as a library: one view (A7OnboardingView) + one ads interface (A7Ads). No ad SDK, no Compose.
// Copy this folder into an app, `include(":a7onboarding")`, `implementation(project(":a7onboarding"))`.
plugins { id("com.android.library") }

android {
    namespace = "namvunhatle.r15.onboarding" // keep: the code refers to its resources through it
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
}
