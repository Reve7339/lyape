import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// CI aporta una clave estable mediante secretos; nunca se usa la firma debug para releases.
val releaseSigning = listOf(
    "LYAPE_RELEASE_STORE_FILE",
    "LYAPE_RELEASE_STORE_PASSWORD",
    "LYAPE_RELEASE_KEY_ALIAS",
    "LYAPE_RELEASE_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSigning.values.any { it != null }
if (hasReleaseSigning) {
    require(releaseSigning.values.all { !it.isNullOrBlank() }) {
        "La configuración de firma release está incompleta. Consulta Publicación en README.md."
    }
}

android {
    namespace = "com.lyape"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.lyape"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "com.lyape.testing.EmulatorTestRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigning.getValue("LYAPE_RELEASE_STORE_FILE")!!)
                storePassword = releaseSigning.getValue("LYAPE_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("LYAPE_RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("LYAPE_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        // Los esquemas de Room se empaquetan en las pruebas instrumentadas para migrar desde v1.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Proyecto 100% Kotlin: evita que Room genere fuentes Java.
    arg("room.generateKotlin", "true")
    // Esquemas versionados: permiten probar la migración real desde la versión 1.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
