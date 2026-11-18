plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The icon and the splash mark, for the builds the owner installs:
// tools/private/wire_art.py copies them into art/res, which is never
// committed. Without them the app has the system's default icon and a plain
// splash (plain/res), and nothing is drawn in their place.
val art = file("art/res")
val hasArt = art.isDirectory
android {
    namespace = "com.cramdown.sorta"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    buildToolsVersion = "37.0.0"
    defaultConfig {
        applicationId = "com.cramdown.sorta"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appIcon"] = if (hasArt) "@mipmap/ic_launcher" else "@android:drawable/sym_def_app_icon"
    }
    sourceSets["main"].res.srcDir(if (hasArt) art else file("plain/res"))
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources = true }
    // S07b, the add-on engine: QuickJS-ng and the shared host (addons/engine), built by src/main/cpp for the
    // two ABIs Sorta ships. -Psorta.addonsEngine=standin leaves the native build out, for local runs without
    // an NDK: the tests' Kotlin stand-in engine answers instead, and CI never sets it.
    if (providers.gradleProperty("sorta.addonsEngine").orNull != "standin") {
        ndkVersion = "30.0.16248370"
        defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "x86_64") } }
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    }
    // SORTA_ADDON_NET (YES unless -Psorta.addonNet=NO) and the add-on directory's index branch, read by AddonConfig
    defaultConfig.manifestPlaceholders["sortaAddonNet"] = providers.gradleProperty("sorta.addonNet").orNull ?: "YES"
    defaultConfig.manifestPlaceholders["sortaAddonsIndexUrl"] = providers.gradleProperty("sorta.addonsIndexUrl").orNull ?: ""
    // the add-on fixtures and conformance cases, for the tests only: resources on the JVM, assets on the emulator
    sourceSets["test"].resources.srcDirs("../../addons/fixtures", "../../addons/conformance")
    sourceSets["androidTest"].assets.srcDirs("../../addons/fixtures", "../../addons/conformance")
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // the look kit (ui/ward): Material 3's theme and sheets, the outlined and rounded icons, and the window's fold (WindowShape)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.window:window:1.5.1")
    testImplementation("junit:junit:4.13.2")
    // the iOS asset catalogue's JSON, read by WardPaletteTest on the plain JVM
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // renders of the states gallery to PNG (WardGalleryRenderTest)
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.77.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // the maps' renders on an emulator (MapRenderTest; ci/public/android.yml's renders job), as
    // Stethoscore's ScreenshotTest ran them
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-junit4 alone brings Espresso 3.5, which looks up InputManager.getInstance by reflection;
    // Android 17 has none, so every test failed before it began (run 40848934413)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
