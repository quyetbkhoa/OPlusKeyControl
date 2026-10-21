plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "dev.opluskeycontrol"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.opluskeycontrol"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":domain"))
    implementation(libs.core)
    implementation(libs.activity)
    implementation(libs.lifecycle)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    implementation(libs.material)
    implementation(libs.icons)
    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
