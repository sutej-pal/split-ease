import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.legacy.kapt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
}

// Apply only when Firebase config is present (file is gitignored).
if (file("google-services.json").exists()) {
    pluginManager.apply("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun localProp(key: String): String =
    localProperties.getProperty(key)
        ?: providers.environmentVariable(key).orNull
        ?: ""

val appVersionProperties =
    Properties().apply {
        val file = rootProject.file("version.properties")
        require(file.exists()) {
            "Missing version.properties at ${file.path}. Create it or restore from git."
        }
        file.inputStream().use { load(it) }
    }
val appVersionCode =
    appVersionProperties.getProperty("versionCode")?.toIntOrNull()
        ?: error("version.properties is missing integer versionCode")
val appVersionName =
    appVersionProperties.getProperty("versionName")?.trim().orEmpty().ifBlank {
        error("version.properties is missing versionName")
    }

val admobAppIdDebug = "ca-app-pub-3940256099942544~3347511713"
val admobBannerUnitIdDebug = "ca-app-pub-3940256099942544/9214589741"
val admobAppIdRelease = localProp("ADMOB_APP_ID").trim()
val admobGroupDetailBannerUnitIdRelease = localProp("ADMOB_GROUP_DETAIL_BANNER_UNIT_ID").trim()
val admobAddExpenseBannerUnitIdRelease = localProp("ADMOB_ADD_EXPENSE_BANNER_UNIT_ID").trim()

val mailServiceBaseUrl = localProp("MAIL_SERVICE_BASE_URL").trim().trimEnd('/')
val inviteWebHost: String =
    try {
        URI(mailServiceBaseUrl).host?.takeIf { it.isNotBlank() } ?: "splitease.app"
    } catch (_: Exception) {
        "splitease.app"
    }

android {
    namespace = "com.splitease.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.splitease.app"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "MAIL_SERVICE_BASE_URL", "\"${localProp("MAIL_SERVICE_BASE_URL")}\"")
        buildConfigField("String", "MAIL_SERVICE_API_KEY", "\"${localProp("MAIL_SERVICE_API_KEY")}\"")
        // Web OAuth client ID from Google Cloud (used by Credential Manager; not a secret).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("GOOGLE_WEB_CLIENT_ID")}\"")
        // Host for https://{host}/invite/{token} browser → app redirects (mail-service).
        manifestPlaceholders["inviteWebHost"] = inviteWebHost
        manifestPlaceholders["admobAppId"] = admobAppIdDebug
        buildConfigField("String", "ADMOB_GROUP_DETAIL_BANNER_UNIT_ID", "\"$admobBannerUnitIdDebug\"")
        buildConfigField("String", "ADMOB_ADD_EXPENSE_BANNER_UNIT_ID", "\"$admobBannerUnitIdDebug\"")
    }

    signingConfigs {
        // Keep Android's default debug keystore for day-to-day installations.
        getByName("debug")

        create("release") {
            val storePath = localProp("KEYSTORE_FILE").trim()
            val storePasswordValue = localProp("KEYSTORE_PASSWORD")
            val keyAliasValue = localProp("KEY_ALIAS").trim()
            val keyPasswordValue = localProp("KEY_PASSWORD")
            if (storePath.isNotEmpty()) {
                storeFile = rootProject.file(storePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val releaseHasAdUnits =
                admobGroupDetailBannerUnitIdRelease.isNotEmpty() ||
                    admobAddExpenseBannerUnitIdRelease.isNotEmpty()
            require(!releaseHasAdUnits || admobAppIdRelease.isNotEmpty()) {
                "Set ADMOB_APP_ID in local.properties (or env) when release AdMob unit IDs are configured."
            }
            // Never embed Google's public test App ID in release APKs. When ads are unconfigured,
            // unit IDs stay empty (AdConfig.isEnabled == false) and MobileAds is never initialized.
            manifestPlaceholders["admobAppId"] =
                admobAppIdRelease.ifEmpty { "ca-app-pub-0000000000000000~0000000000" }
            buildConfigField(
                "String",
                "ADMOB_GROUP_DETAIL_BANNER_UNIT_ID",
                "\"$admobGroupDetailBannerUnitIdRelease\"",
            )
            buildConfigField(
                "String",
                "ADMOB_ADD_EXPENSE_BANNER_UNIT_ID",
                "\"${admobAddExpenseBannerUnitIdRelease.ifEmpty { admobGroupDetailBannerUnitIdRelease }}\"",
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
        debug {
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/*.kotlin_module",
                    "META-INF/INDEX.LIST",
                    "META-INF/DEPENDENCIES",
                    "DebugProbesKt.bin",
                    "kotlin-tooling-metadata.json",
                )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.biometric)
    implementation(libs.play.install.referrer)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.lottie.compose)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    // OkHttp engine: WebSocket-capable (Realtime) and cancel-safe on Main
    // (ktor-client-android can NetworkOnMainThreadException when closing responses).
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}

ktlint {
    android.set(true)
    ignoreFailures.set(false)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
