import java.util.Properties

plugins {
    id("com.android.application")
}

fun loadEnvironmentProperties(environment: String): Properties {
    val properties = Properties()
    val exampleFile = rootProject.file("config/$environment.properties.example")
    val localFile = rootProject.file("config/$environment.properties")

    if (exampleFile.exists()) {
        exampleFile.inputStream().use(properties::load)
    }

    if (localFile.exists()) {
        localFile.inputStream().use(properties::load)
    }

    return properties
}

fun Properties.requireValue(key: String, environment: String): String =
    getProperty(key)?.takeIf { it.isNotBlank() }
        ?: error("Missing '$key' for Android environment '$environment'.")

fun asBuildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.kriyanshtech.bodycam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kriyanshtech.bodycam"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            val env = "dev"
            val props = loadEnvironmentProperties(env)
            dimension = "environment"
            applicationIdSuffix = ".dev"
            manifestPlaceholders["appName"] = "BodyCam Dev"
            buildConfigField(
                "String",
                "DEFAULT_BACKEND_URL",
                asBuildConfigString(props.requireValue("DEFAULT_BACKEND_URL", env))
            )
        }
        create("staging") {
            val env = "staging"
            val props = loadEnvironmentProperties(env)
            dimension = "environment"
            applicationIdSuffix = ".staging"
            manifestPlaceholders["appName"] = "BodyCam Staging"
            buildConfigField(
                "String",
                "DEFAULT_BACKEND_URL",
                asBuildConfigString(props.requireValue("DEFAULT_BACKEND_URL", env))
            )
        }
        create("prod") {
            val env = "prod"
            val props = loadEnvironmentProperties(env)
            dimension = "environment"
            manifestPlaceholders["appName"] = "BodyCam"
            buildConfigField(
                "String",
                "DEFAULT_BACKEND_URL",
                asBuildConfigString(props.requireValue("DEFAULT_BACKEND_URL", env))
            )
        }
    }

    signingConfigs {
        create("release") {
            val props = Properties()
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                localPropertiesFile.inputStream().use { props.load(it) }
            }

            storeFile = props.getProperty("release.keystore")?.let { file(it) }
            storePassword = props.getProperty("release.keystore.password")
            keyAlias = props.getProperty("release.key.alias")
            keyPassword = props.getProperty("release.key.password")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            lint {
                checkReleaseBuilds = false
                abortOnError = false
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.camera:camera-core:1.6.1")
    implementation("androidx.camera:camera-camera2:1.6.1")
    implementation("androidx.camera:camera-lifecycle:1.6.1")
    implementation("androidx.camera:camera-video:1.6.1")
    implementation("androidx.camera:camera-view:1.6.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("io.livekit:livekit-android:2.25.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-moshi:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.3.2")
}
