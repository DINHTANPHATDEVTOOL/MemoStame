plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.secrets)
}

// Process google-services.json only when external Firebase configuration is present.
// Normal CI builds without google-services.json continue building cleanly without crashing.
val hasGoogleServicesConfig = file("google-services.json").exists() ||
    file("src/release/google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    (project.findProperty("googleServicesJsonPath") as? String)?.let { file(it).exists() } == true

if (hasGoogleServicesConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// Versioning overrides for Release Candidate / Store builds
val envVersionCode = System.getenv("MEMOSTAMP_VERSION_CODE")
    ?: (project.findProperty("MEMOSTAMP_VERSION_CODE") as? String)
val parsedVersionCode = if (!envVersionCode.isNullOrBlank()) {
    val code = envVersionCode.toIntOrNull()
        ?: throw IllegalArgumentException("MEMOSTAMP_VERSION_CODE must be a valid integer, got: '$envVersionCode'")
    require(code > 0) { "MEMOSTAMP_VERSION_CODE must be a positive integer, got: $code" }
    code
} else {
    1
}

val envVersionName = System.getenv("MEMOSTAMP_VERSION_NAME")
    ?: (project.findProperty("MEMOSTAMP_VERSION_NAME") as? String)
val parsedVersionName = if (!envVersionName.isNullOrBlank()) {
    require(envVersionName.matches(Regex("^[0-9]+(\\.[0-9]+)+(-[a-zA-Z0-9.]+)?$"))) {
        "MEMOSTAMP_VERSION_NAME is malformed: '$envVersionName'. Expected format like 1.0, 1.0.1, or 1.0.0-rc1"
    }
    envVersionName
} else {
    "1.0"
}

android {
    namespace = "com.mipastudio.memostamp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mipastudio.memostamp"
        minSdk = 26
        targetSdk = 36
        versionCode = parsedVersionCode
        versionName = parsedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val envPrivacyUrl = System.getenv("MEMOSTAMP_PRIVACY_POLICY_URL")
            ?: (project.findProperty("MEMOSTAMP_PRIVACY_POLICY_URL") as? String)
            ?: "https://memostamp.mipastudio.com/privacy"
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$envPrivacyUrl\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("MEMOSTAMP_RELEASE_STORE_FILE")
                ?: (project.findProperty("MEMOSTAMP_RELEASE_STORE_FILE") as? String)
            val storePassword = System.getenv("MEMOSTAMP_RELEASE_STORE_PASSWORD")
                ?: (project.findProperty("MEMOSTAMP_RELEASE_STORE_PASSWORD") as? String)
            val keyAlias = System.getenv("MEMOSTAMP_RELEASE_KEY_ALIAS")
                ?: (project.findProperty("MEMOSTAMP_RELEASE_KEY_ALIAS") as? String)
            val keyPassword = System.getenv("MEMOSTAMP_RELEASE_KEY_PASSWORD")
                ?: (project.findProperty("MEMOSTAMP_RELEASE_KEY_PASSWORD") as? String)
            val requireSigning = (System.getenv("MEMOSTAMP_REQUIRE_RELEASE_SIGNING")
                ?: (project.findProperty("MEMOSTAMP_REQUIRE_RELEASE_SIGNING") as? String))?.toBoolean() ?: false

            val hasAllSigningProperties = !storeFilePath.isNullOrBlank() &&
                !storePassword.isNullOrBlank() &&
                !keyAlias.isNullOrBlank() &&
                !keyPassword.isNullOrBlank()

            if (hasAllSigningProperties) {
                val resolvedStoreFile = file(storeFilePath!!)
                if (requireSigning && !resolvedStoreFile.exists()) {
                    throw IllegalArgumentException("MEMOSTAMP_RELEASE_STORE_FILE does not exist at: $storeFilePath")
                }
                storeFile = resolvedStoreFile
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else if (requireSigning) {
                val missing = mutableListOf<String>()
                if (storeFilePath.isNullOrBlank()) missing.add("MEMOSTAMP_RELEASE_STORE_FILE")
                if (storePassword.isNullOrBlank()) missing.add("MEMOSTAMP_RELEASE_STORE_PASSWORD")
                if (keyAlias.isNullOrBlank()) missing.add("MEMOSTAMP_RELEASE_KEY_ALIAS")
                if (keyPassword.isNullOrBlank()) missing.add("MEMOSTAMP_RELEASE_KEY_PASSWORD")
                throw IllegalArgumentException("Production release signing is required (MEMOSTAMP_REQUIRE_RELEASE_SIGNING=true), but missing properties: ${missing.joinToString(", ")}")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets {
    propertiesFileName = ".env"
    defaultPropertiesFileName = ".env.example"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.googlefonts)
    implementation(libs.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.firebase:firebase-messaging:24.1.0")
    implementation(libs.coil.compose)
    implementation(libs.google.places)
    implementation(libs.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}