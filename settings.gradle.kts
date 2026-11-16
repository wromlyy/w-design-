pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "W-Design"

// Only modules that exist are included; planned feature modules (editor-kit, theme-studio,
// create, importexport, launcher, widgets, livewallpaper) are added when their stage lands.
include(":app")
include(":core:model", ":core:format", ":core:mtz", ":core:data", ":core:device", ":core:render")
include(":design-system")
include(
    ":features:home",
    ":features:discover",
    ":features:designs",
    ":features:settings",
    ":features:xiaomilab",
    ":features:lockscreen",
)

// Local build cache inside the project folder (gitignored) so finished task outputs survive a
// fresh build machine; CI uses gradle/actions/setup-gradle caching instead.
buildCache {
    local { directory = File(rootDir, ".gradle-build-cache") }
}
