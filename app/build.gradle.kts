import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun releaseSigningProperty(name: String): String? =
    localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

val releaseSigningKeys = listOf(
    "RECODER_STORE_FILE",
    "RECODER_STORE_PASSWORD",
    "RECODER_KEY_ALIAS",
    "RECODER_KEY_PASSWORD",
)

val missingReleaseSigningKeys = releaseSigningKeys.filter { releaseSigningProperty(it) == null }

gradle.taskGraph.whenReady {
    val requiresReleaseSigning = allTasks.any { task ->
        val taskName = task.name.lowercase()
        taskName.contains("release") &&
            (taskName.contains("assemble") ||
                taskName.contains("bundle") ||
                taskName.contains("package") ||
                taskName.contains("sign"))
    }

    if (requiresReleaseSigning && missingReleaseSigningKeys.isNotEmpty()) {
        throw GradleException(
            "Release signing is missing: ${missingReleaseSigningKeys.joinToString()}. " +
                "Set them in local.properties or environment variables.",
        )
    }
}

android {
    namespace = "com.recoder.stockledger"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.recoder.stockledger"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (missingReleaseSigningKeys.isEmpty()) {
                storeFile = file(releaseSigningProperty("RECODER_STORE_FILE")!!)
                storePassword = releaseSigningProperty("RECODER_STORE_PASSWORD")
                keyAlias = releaseSigningProperty("RECODER_KEY_ALIAS")
                keyPassword = releaseSigningProperty("RECODER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "StockLedger Debug")
        }

        release {
            isMinifyEnabled = false
            if (missingReleaseSigningKeys.isEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += setOf(
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
            )
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("org.eclipse.angus:jakarta.mail:2.0.0")
    implementation("org.eclipse.angus:angus-activation:2.0.0")
    implementation("jakarta.activation:jakarta.activation-api:2.1.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.apache.pdfbox:pdfbox:2.0.29")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}

