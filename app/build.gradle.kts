import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.kid.babylog"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kid.babylog"
        minSdk = 26
        // Не поднимаем без проверки на телефоне: с 35 Android включает edge-to-edge и меняет раскладку экрана
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        // Ключ релизов есть только в секретах CI и у владельца, в репозитории его нет (ADR-0012)
        System.getenv("BABYLOG_KEYSTORE")?.let { keystore ->
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("BABYLOG_KEYSTORE_PASSWORD")
                keyAlias = "babylog"
                keyPassword = System.getenv("BABYLOG_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Отдельное приложение рядом с основным: отладка не трогает настоящие записи на телефоне
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // Без ключа release собирается неподписанным: чужой подписью его не выдать за релиз проекта
            signingConfig = signingConfigs.findByName("release")
        }
    }
    // Блок метаданных зависимостей в APK зашифрован ключом Google — открытому проекту он не нужен
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    // Robolectric: ресурсы и шрифты приложения в тестах на JVM (ADR-0013)
    testOptions.unitTests.isIncludeAndroidResources = true
    // JSON-схемы Room для тестов миграций. Тесты на JVM видят только assets тестируемого варианта,
    // поэтому схемы — в debug (несколько КБ в debug APK, в release их нет)
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

// Эталонные скриншоты лежат в репозитории; обновляются задачей recordRoborazziDebug (ADR-0013)
roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // @Preview и Layout Inspector в Android Studio; в release не попадает
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    testImplementation("junit:junit:4.13.2")
    // Тесты ViewModel на JVM (Dispatchers.Main, runTest); версия — как у kotlinx-coroutines в приложении
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    // Android на JVM без эмулятора: миграции, Room и скриншоты (ADR-0013)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.76.0")
}
