pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "A7Onboarding"
// a7onboarding = the library (the one folder to copy into an app) · sample = demo app with mock ads.
// Real ads: the app implements A7Ads with its own SDK.
include(":a7onboarding", ":sample")
