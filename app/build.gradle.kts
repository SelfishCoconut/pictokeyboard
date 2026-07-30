plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

android {
    namespace = "org.pictokeyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.pictokeyboard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Errors fail CI; warnings are reported but do not block.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        // A string present in one locale and missing in another is a shipping
        // defect, not a warning. Never disable the Accessibility category.
        error += listOf("MissingTranslation", "ExtraTranslation")
        htmlReport = true
        xmlReport = true
        // Same contract as the detekt baseline: existing prototype findings are
        // recorded so they do not block, and anything NEW fails the build.
        // Regenerate only when the debt is actually paid down (delete the file
        // and re-run lint) -- never to silence a fresh finding.
        baseline = file("lint-baseline.xml")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// Passed explicitly rather than relying on .editorconfig discovery, which
// Spotless does not apply to its embedded ktlint step. .editorconfig still
// exists for the IDE; these are the values CI actually enforces.
val ktlintRules = mapOf(
    // Composables are PascalCase by Compose API convention, not a violation.
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    // ktlint cannot auto-correct line length, so leaving it on makes
    // spotlessApply fail instead of format. detekt owns this rule; its
    // baseline records existing long lines so new ones still fail CI.
    "ktlint_standard_max-line-length" to "disabled",
    // Misreads Compose slot APIs (trailing content lambda after modifier).
    "ktlint_standard_function-signature" to "disabled",
)

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintRules)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    // The baseline records the debt that exists today so it does not block,
    // while any NEW finding fails the build. Regenerate it only when the debt
    // is genuinely paid down -- never to silence a fresh finding.
    baseline = file("detekt-baseline.xml")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "17"
    reports {
        xml.required.set(true)
        html.required.set(true)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "17"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    implementation(libs.google.material)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)

    implementation(libs.coil.compose)
    implementation(libs.coil)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
