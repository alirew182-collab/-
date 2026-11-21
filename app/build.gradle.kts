import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.neuron.academy"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.neuron.academy"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "2.6"
        // Встроенная модель работает на 64-битных устройствах
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Постоянный ключ подписи: одна и та же подпись у всех версий, поэтому новая версия
    // ставится поверх старой как обновление и прогресс сохраняется.
    // Файл ключа зашифрован; пароль берётся из секрета GitHub NEURON_KEYSTORE_PASSWORD.
    val releasePassword = System.getenv("NEURON_KEYSTORE_PASSWORD").orEmpty()
    val releaseKeystore = file("neuron-release.p12")
    signingConfigs {
        create("release") {
            storeFile = releaseKeystore
            storeType = "PKCS12"
            storePassword = releasePassword
            keyAlias = "neuron"
            keyPassword = releasePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (releasePassword.isNotEmpty() && releaseKeystore.exists()) {
                signingConfigs.getByName("release")
            } else {
                logger.warn("NEURON_KEYSTORE_PASSWORD не задан: сборка подписана временным debug-ключом и не встанет поверх установленной версии")
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/io.netty.versions.properties")
        jniLibs.pickFirsts += "**/liblitertlm_jni.so"
        // нативные библиотеки модели не сжимаем: быстрее загрузка
        jniLibs.useLegacyPackaging = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // LiteRT-LM собран более новым Kotlin — читаем его метаданные
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":gamification"))

    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Room
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")

    // Сеть: Ktor + kotlinx.serialization
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Шифрование ключей
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Встроенный ИИ: LiteRT-LM (Google) — модели Gemma 4 / Qwen 3.5 прямо на телефоне, ускорение на GPU
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.1")

    // Виджеты рабочего стола
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Уведомления по расписанию
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
}
