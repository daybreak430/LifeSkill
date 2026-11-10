plugins {
    id("com.android.application") version "9.3.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("com.google.devtools.ksp") version "2.3.10"
}

android {
    namespace = "com.lifeskill.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lifeskill.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.4.3"
        testInstrumentationRunner = "com.lifeskill.app.LifeSkillTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("org.commonmark:commonmark:0.32.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.32.0")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
