import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Cloudinary unsigned upload settings, kept out of Git in local.properties:
//   cloudinary.cloudName=<cloud name>
//   cloudinary.uploadPreset=<unsigned preset name>
// Never put the API secret here: everything in BuildConfig ships inside the APK.
// Missing values build fine; uploads then fail with "Cloudinary is not configured".
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { load(it.reader()) }
}
fun cloudinaryProperty(key: String): String =
    (localProperties.getProperty(key) ?: providers.gradleProperty(key).orNull).orEmpty()

android {
    namespace = "com.wandervox.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 27

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", "\"${cloudinaryProperty("cloudinary.cloudName")}\"")
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", "\"${cloudinaryProperty("cloudinary.uploadPreset")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        // lifecycle 2.9.x and Compose runtime 1.10.x ship these checks compiled against a newer
        // Lint API than AGP 8.7.3, which crashes the lint run (IncompatibleClassChangeError).
        // Drop once AGP >= 8.9.
        disable += setOf("NullSafeMutableLiveData", "FrequentlyChangingValue", "RememberInComposition")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Offline speech-to-text (pulls the JNA .aar with its native libs transitively)
    implementation(libs.vosk.android)

    // On-device translation
    implementation(libs.mlkit.translate)

    // Remote (Track B): sync del Travel Log en Firestore y subida unsigned a Cloudinary vía HTTP
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // The android.jar used by unit tests only has throwing stubs of org.json.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
