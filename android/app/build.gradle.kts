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
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // the look kit (ui/ward): Material 3's theme and sheets, the rounded icons, and the window's fold (WindowShape)
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
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
