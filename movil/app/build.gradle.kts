plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.github.takahirom.roborazzi")
}
android {
    namespace = "com.gonzalo.gamerate"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gonzalo.gamerate"
        minSdk = 29
        targetSdk = 34
        versionCode = 67
        versionName = "6.39"
        // El traductor del lector trae código nativo por procesador: móviles de 64 bits (arm64) y el emulador de pruebas (x86_64)
        // (-Pabis=arm64-v8a genera la del móvil sola, más ligera)
        ndk { abiFilters += ((project.findProperty("abis") as String?)?.split(",") ?: listOf("arm64-v8a", "x86_64")) }
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "3g"
                // Cada clase de pruebas en su propio proceso: ninguna hereda lo que dejó otra (estado global de Compose)
                it.forkEvery = 1
                // Robolectric sin internet: su Android de prueba lo baja Gradle (ver robolectricDeps)
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("robolectric.dependency.dir", layout.buildDirectory.dir("robolectric-deps").get().asFile.absolutePath)
                it.dependsOn("copyRobolectricDeps")
            }
        }
    }
}
val robolectricDeps: Configuration by configurations.creating
tasks.register<Copy>("copyRobolectricDeps") {
    from(robolectricDeps)
    into(layout.buildDirectory.dir("robolectric-deps"))
}

dependencies {
    robolectricDeps("org.robolectric:android-all-instrumented:14-robolectric-10818077-i6")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Lector: traducir palabras sin conexión (el idioma se baja una vez) y saber en qué idioma está el texto
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    // Pruebas de la lógica en el ordenador (sin emulador): org.json de verdad en lugar del de Android
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Capturas de pantalla en el ordenador (Robolectric + Roborazzi): para revisar el diseño sin emulador
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
