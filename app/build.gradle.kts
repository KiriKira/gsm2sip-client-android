plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStoreFile = System.getenv("GSM_RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }
val releaseStorePassword = System.getenv("GSM_RELEASE_STORE_PASSWORD")?.takeIf { it.isNotEmpty() }
val releaseKeyAlias = System.getenv("GSM_RELEASE_KEY_ALIAS")?.takeIf { it.isNotEmpty() }
val releaseKeyPassword = System.getenv("GSM_RELEASE_KEY_PASSWORD")?.takeIf { it.isNotEmpty() }
val releaseVersionCodeText = System.getenv("GSM_RELEASE_VERSION_CODE")?.takeIf { it.isNotBlank() }
val releaseVersionName = System.getenv("GSM_RELEASE_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.3.0"
val appVersionCode = releaseVersionCodeText?.toIntOrNull()?.also {
    if (it !in 1..2_100_000_000) {
        throw GradleException("GSM_RELEASE_VERSION_CODE must be between 1 and 2100000000.")
    }
} ?: if (releaseVersionCodeText == null) {
    4
} else {
    throw GradleException("GSM_RELEASE_VERSION_CODE must be a positive integer.")
}

fun releasePackagingRequested(taskNames: Iterable<String>): Boolean = taskNames.any { name ->
    val taskName = name.substringAfterLast(':')
    taskName.contains("Release", ignoreCase = true) &&
        listOf("assemble", "package", "bundle", "sign", "validateSigning")
            .any { prefix -> taskName.startsWith(prefix, ignoreCase = true) }
}

android {
    namespace = "com.callagent.host"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callagent.host"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    signingConfigs {
        create("release") {
            releaseStoreFile?.let { storeFile = file(it) }
            releaseStorePassword?.let { storePassword = it }
            releaseKeyAlias?.let { keyAlias = it }
            releaseKeyPassword?.let { keyPassword = it }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

gradle.taskGraph.whenReady {
    if (releasePackagingRequested(allTasks.map { it.name })) {
        val missing = buildList {
            if (releaseStoreFile == null) add("GSM_RELEASE_STORE_FILE")
            if (releaseStorePassword == null) add("GSM_RELEASE_STORE_PASSWORD")
            if (releaseKeyAlias == null) add("GSM_RELEASE_KEY_ALIAS")
            if (releaseKeyPassword == null) add("GSM_RELEASE_KEY_PASSWORD")
            if (releaseVersionCodeText == null) add("GSM_RELEASE_VERSION_CODE")
            if (System.getenv("GSM_RELEASE_VERSION_NAME").isNullOrBlank()) add("GSM_RELEASE_VERSION_NAME")
        }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "A signed release build requires these environment variables: ${missing.joinToString()}. " +
                    "Configure the permanent release keystore and credentials; no development or random key is generated."
            )
        }
        if (!file(releaseStoreFile!!).isFile) {
            throw GradleException("GSM_RELEASE_STORE_FILE does not point to a readable release keystore file.")
        }
    }
}

dependencies {
    implementation(files("libs/pjsua2-2.17-dev.aar"))
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-livedata-core:2.8.7") {
        version { strictly("2.8.7") }
    }
    implementation("androidx.window:window:1.5.1")
    implementation("androidx.window:window-java:1.5.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.14.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
}
