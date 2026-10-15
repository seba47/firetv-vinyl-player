plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.vinilotv"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.vinilotv"
        // Fire OS 7 = API 28, Fire OS 8 = API 30.
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-spike"
    }

    compileOptions {
        // NewPipeExtractor usa APIs de Java que no están en todas las versiones de Android.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)

    implementation(libs.newpipe.extractor)
    implementation(libs.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    implementation(libs.lifecycle.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)

    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // scripts/smoke-test.sh pasa la búsqueda con -Pquery="..."
    systemProperty("vinilo.query", providers.gradleProperty("query").getOrElse(""))
    systemProperty("vinilo.url", providers.gradleProperty("url").getOrElse(""))
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    outputs.upToDateWhen { false }
}
