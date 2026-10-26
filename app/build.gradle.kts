plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.speakpen.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.speakpen.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "0.6.0-dev02"
        val serviceUrl = providers.gradleProperty("speakpen.serviceUrl").orElse(providers.environmentVariable("SPEAKPEN_BACKEND_URL")).orElse("").get().trim().trimEnd('/')
        require(serviceUrl.isEmpty() || serviceUrl.startsWith("https://")) { "Use an HTTPS service URL." }
        buildConfigField("String", "SERVICE_BASE_URL", "\"${serviceUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        val accessToken = providers.environmentVariable("SPEAKPEN_SERVICE_TOKEN").orElse("").get()
        buildConfigField("String", "SERVICE_ACCESS_TOKEN", "\"${accessToken.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}






