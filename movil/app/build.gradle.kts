plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.gonzalo.gamerate"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gonzalo.gamerate"
        minSdk = 29
        targetSdk = 34
        versionCode = 22
        versionName = "5.2"
    }
    // Firma fija: así cada versión nueva se instala encima de la anterior sin borrar tus datos.
    signingConfigs {
        getByName("debug") {
            storeFile = file("valorin.keystore")
            storePassword = "valorin"
            keyAlias = "valorin"
            keyPassword = "valorin"
        }
        create("release") {
            storeFile = file("valorin.keystore")
            storePassword = "valorin"
            keyAlias = "valorin"
            keyPassword = "valorin"
        }
    }
    // Versión "release": optimizada con R8 y sin modo depuración. Es mucho más fluida que la de depuración,
    // y como va firmada con la misma clave se instala encima sin perder nada.
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Pruebas de la lógica en el ordenador (sin emulador): org.json de verdad en lugar del de Android
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
