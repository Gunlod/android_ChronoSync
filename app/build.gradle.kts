import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val uploadSigning = Properties().apply {
    val configFile = rootProject.file("keystore.properties")
    if (configFile.isFile) configFile.inputStream().use { load(it) }
}
fun signingValue(name: String, environment: String): String? =
    providers.environmentVariable(environment).orNull?.takeIf { it.isNotBlank() }
        ?: uploadSigning.getProperty(name)?.takeIf { it.isNotBlank() }
val uploadStore = signingValue("storeFile", "CHRONOSYNC_STORE_FILE")
val uploadStorePassword = signingValue("storePassword", "CHRONOSYNC_STORE_PASSWORD")
val uploadAlias = signingValue("keyAlias", "CHRONOSYNC_KEY_ALIAS")
val uploadKeyPassword = signingValue("keyPassword", "CHRONOSYNC_KEY_PASSWORD")
val signingValues = listOf(uploadStore, uploadStorePassword, uploadAlias, uploadKeyPassword)
val hasUploadSigning = signingValues.all { it != null }
check(signingValues.none { it != null } || hasUploadSigning) {
    "Upload signing configuration is incomplete. Set all four values in keystore.properties or CHRONOSYNC_* environment variables."
}
check(!providers.gradleProperty("chronosync.requireReleaseSigning").map(String::toBoolean).getOrElse(false) || hasUploadSigning) {
    "A signed release requires a signing key. See docs/WEB_APK_RELEASE.md or docs/PLAY_STORE_RELEASE.md."
}
android {
    namespace = "jp.gunlod.chronosync"
    compileSdk = 36
    defaultConfig {
        applicationId = "jp.gunlod.chronosync"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (hasUploadSigning) create("upload") {
            storeFile = rootProject.file(uploadStore!!)
            storePassword = uploadStorePassword
            keyAlias = uploadAlias
            keyPassword = uploadKeyPassword
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            if (hasUploadSigning) signingConfig = signingConfigs.getByName("upload")
        }
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
