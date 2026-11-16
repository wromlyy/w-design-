import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/*
 * Release signing never reads secrets from the repository. Sources, in order:
 *  1. keystore.properties (gitignored) with storeFile/storePassword/keyAlias/keyPassword
 *  2. env vars WD_KEYSTORE_PATH, WD_STORE_PASSWORD, WD_KEY_ALIAS, WD_KEY_PASSWORD (CI decodes the
 *     WD_KEYSTORE_BASE64 secret into $RUNNER_TEMP and sets WD_KEYSTORE_PATH)
 * Without either, the release stays unsigned and Gradle prints a warning. See docs/SIGNING.md.
 */
val signingProps: Map<String, String>? = run {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) {
        val p = Properties().apply { f.inputStream().use(::load) }
        mapOf(
            "storeFile" to p.getProperty("storeFile", ""),
            "storePassword" to p.getProperty("storePassword", ""),
            "keyAlias" to p.getProperty("keyAlias", ""),
            "keyPassword" to p.getProperty("keyPassword", ""),
        )
    } else {
        val path = System.getenv("WD_KEYSTORE_PATH")
        if (!path.isNullOrBlank()) mapOf(
            "storeFile" to path,
            "storePassword" to (System.getenv("WD_STORE_PASSWORD") ?: ""),
            "keyAlias" to (System.getenv("WD_KEY_ALIAS") ?: ""),
            "keyPassword" to (System.getenv("WD_KEY_PASSWORD") ?: ""),
        ) else null
    }
}?.takeIf { it.values.all(String::isNotBlank) && file(it.getValue("storeFile")).isFile }

if (signingProps == null) {
    logger.warn("W Design: no release keystore configured (keystore.properties or WD_* env vars). The release APK will be UNSIGNED. See docs/SIGNING.md.")
}

android {
    namespace = "com.wdesign.studio"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wdesign.studio"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // The app ships English and Russian only; drop library translations for other languages.
        resourceConfigurations += listOf("en", "ru")
    }

    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = file(signingProps.getValue("storeFile"))
                storePassword = signingProps.getValue("storePassword")
                keyAlias = signingProps.getValue("keyAlias")
                keyPassword = signingProps.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 is always on for CI and normal builds. -Pwd.localNoMinify=true is only for very low-memory
            // local machines where R8 cannot finish; such an APK is functionally identical but larger.
            val minify = !(project.findProperty("wd.localNoMinify")?.toString()?.toBoolean() ?: false)
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingProps != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
    packaging { resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.kotlin_module") }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(project(":design-system"))
    implementation(project(":core:data"))
    implementation(project(":features:home"))
    implementation(project(":features:discover"))
    implementation(project(":features:designs"))
    implementation(project(":features:settings"))
    implementation(project(":features:xiaomilab"))
    implementation(project(":features:lockscreen"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.haze)
}
