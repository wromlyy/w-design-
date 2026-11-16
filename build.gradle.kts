import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

/**
 * Shared configuration for every Android library module (core/data, core/device, core/render,
 * design-system and all feature modules). Feature modules get the common UI stack automatically so
 * their own build files only list module-specific dependencies.
 */
subprojects {
    if (path.startsWith(":features:")) {
        apply(plugin = "com.android.library")
        apply(plugin = "org.jetbrains.kotlin.android")
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        val featureName = name
        extensions.configure<LibraryExtension> {
            namespace = "com.wdesign.feature." + featureName.replace("-", "")
            compileSdk = 35
            defaultConfig { minSdk = 26 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            buildFeatures { compose = true }
            lint { abortOnError = false; checkReleaseBuilds = false }
        }
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
        dependencies {
            add("implementation", platform(rootProject.libs.compose.bom))
            add("implementation", project(":design-system"))
            add("implementation", project(":core:data"))
            add("implementation", rootProject.libs.compose.ui)
            add("implementation", rootProject.libs.compose.foundation)
            add("implementation", rootProject.libs.compose.material3)
            add("implementation", rootProject.libs.androidx.core.ktx)
            add("implementation", rootProject.libs.androidx.activity.compose)
            add("implementation", rootProject.libs.androidx.lifecycle.runtime.compose)
            add("implementation", rootProject.libs.coroutines.android)
            add("implementation", rootProject.libs.haze)
            if (featureName == "home") add("implementation", project(":features:xiaomilab"))
        }
    }
}

// Guard: a feature module without the Android library plugin means the block above silently did not run.
gradle.projectsEvaluated {
    subprojects.filter { it.path.startsWith(":features:") }.forEach { p ->
        check(p.plugins.hasPlugin("com.android.library")) { "${p.path} is missing com.android.library: check the root subprojects block" }
    }
}
