plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.wdesign.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:format"))
    api(project(":core:mtz"))
    api(project(":core:device"))
    api(project(":core:render"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.datastore.preferences)
    api(libs.coroutines.android)
}
