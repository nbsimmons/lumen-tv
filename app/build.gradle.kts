import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signing = Properties().apply {
    val file = File(System.getenv("LUMEN_SIGNING") ?: "${System.getProperty("user.home")}/.lumen/signing.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val releaseSigned = signing.getProperty("storeFile") != null

android {
    namespace = "app.lumen.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.lumen.tv"
        minSdk = 25
        targetSdk = 36
        versionCode = providers.gradleProperty("lumen.versionCode").get().toInt()
        versionName = providers.gradleProperty("lumen.versionName").get()
    }

    signingConfigs {
        if (releaseSigned) {
            create("release") {
                storeFile = file(signing.getProperty("storeFile"))
                storePassword = signing.getProperty("storePassword")
                keyAlias = signing.getProperty("keyAlias")
                keyPassword = signing.getProperty("keyPassword")
            }
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("sideload") {
            dimension = "distribution"
            buildConfigField("boolean", "NATIVE_TRAILERS", "true")
            buildConfigField("boolean", "IMDB_DATASET", "true")
            buildConfigField("String", "UPDATE_REPO", "\"${providers.gradleProperty("lumen.updateRepo").getOrElse("")}\"")
            proguardFile("proguard-sideload.pro")
        }
        create("store") {
            dimension = "distribution"
            buildConfigField("boolean", "NATIVE_TRAILERS", "false")
            buildConfigField("boolean", "IMDB_DATASET", "false")
            buildConfigField("String", "UPDATE_REPO", "\"\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseSigned) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.tv.material)

    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)

    implementation(libs.jellyfin.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)

    "sideloadImplementation"(libs.newpipe.extractor)
}
