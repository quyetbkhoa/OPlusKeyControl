plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
// Deliberate local opt-in build only. Normal APKs retain self scope and no policy hooks.
val policyProbe = providers.gradleProperty("opcPolicyProbe").orNull == "true"
android {
    namespace = "dev.opluskeycontrol"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.opluskeycontrol"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
        buildConfigField("boolean", "POLICY_PROBE", policyProbe.toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").resources.directories.apply {
        clear()
        add(if (policyProbe) "src/policyProbe/resources" else "src/main/resources")
    }
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
    compileOnly(libs.libxposed.api)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.json)
}
