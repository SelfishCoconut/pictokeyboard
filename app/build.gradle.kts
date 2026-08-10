import java.util.Properties

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

// Room writes the schema of every version to `app/schemas`, and those files are
// committed. They are what MigrationTestHelper opens to build a real database
// at the old version before running a migration against it -- without them a
// migration test can only assert what the migration says it does, not that the
// resulting schema is the one the entities describe. Committing them also makes
// a schema change visible in review as a diff rather than as a version bump.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Release signing material and the version code, by the names release.yml
// already passes them under. An environment variable is how CI supplies them;
// the Gradle property fallback is for signing a build by hand without putting
// a password on the command line (put them in ~/.gradle/gradle.properties,
// never in the repo).
fun releaseSecret(name: String): String? =
    System.getenv(name) ?: providers.gradleProperty(name).orNull

// Absent on any machine without the upload key, which is every machine except
// the release runner. Checked for existence rather than trusted: a path that
// does not resolve makes AGP fail at execution time with a stack trace instead
// of the plain "built unsigned" that a developer actually wants.
val upstreamKeystore = releaseSecret("KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }

// Supabase credentials. The anon key is public by design -- row-level security
// is the boundary, not secrecy -- but it still arrives through local.properties
// rather than the repository, so a fork does not inherit this project's backend.
// The service_role key must NEVER appear here or anywhere else in the repo.
//
// A separate reader from releaseSecret because the fallbacks differ: signing
// material comes from ~/.gradle/gradle.properties, while these are per-checkout
// and belong in local.properties next to sdk.dir.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun supabaseSecret(name: String): String =
    System.getenv(name) ?: localProps.getProperty(name).orEmpty()

android {
    namespace = "org.pictokeyboard"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.pictokeyboard"
        minSdk = 26
        targetSdk = 35
        // Play burns a versionCode permanently on upload -- including on an
        // upload that was then rejected -- so it must never repeat. CI passes
        // the run number, which is monotonic. A local build gets 1, which is
        // fine because a locally-built artifact never reaches the console.
        versionCode = releaseSecret("VERSION_CODE")?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Empty on any machine that has not been given a project -- a fresh
        // clone, CI, a fork. SupabaseConfig.isConfigured reads exactly this, and
        // the app then hides accounts entirely rather than offering a dead
        // button. An account is never required to use the keyboard, so a build
        // without a backend is a supported build and not a broken one.
        buildConfigField("String", "SUPABASE_URL", "\"${supabaseSecret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${supabaseSecret("SUPABASE_ANON_KEY")}\"")
        // The OAuth *web* client id, not the Android one -- Credential Manager
        // wants the server client id. Empty hides the Google button rather than
        // offering one that can only fail.
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"${supabaseSecret("GOOGLE_SERVER_CLIENT_ID")}\"",
        )
    }

    signingConfigs {
        create("release") {
            upstreamKeystore?.let {
                storeFile = it
                storePassword = releaseSecret("KEYSTORE_PASSWORD")
                keyAlias = releaseSecret("KEY_ALIAS")
                keyPassword = releaseSecret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Left unsigned rather than falling back to the debug key when
            // there is no keystore. Play rejects a debug-signed artifact, and a
            // debug key here would turn that rejection into something only the
            // console tells you about -- after the versionCode is spent.
            // release.yml's apksigner step is what catches an unsigned upload.
            signingConfig = upstreamKeystore?.let { signingConfigs.getByName("release") }
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

    // MigrationTestHelper reads the exported schemas from the test APK's assets,
    // so the directory ksp writes them to has to be an androidTest asset source.
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
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
    // Overlay for the handful of default rules that misread Compose. See the
    // file's own header for why each entry is a misconfiguration and not a
    // suppression.
    // One config, not two: `setFrom` REPLACES rather than appends, so a second
    // call silently discards the first. #34 and #14 each added their own file;
    // this one is the superset (it carries #34's FunctionNaming stanza verbatim
    // plus four more overlays), so the other was removed rather than layered.
    config.setFrom(files("config/detekt.yml"))
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
    androidTestImplementation(libs.androidx.room.testing)

    // room-testing parses the exported schema JSON with kotlinx-serialization
    // 1.8, whose `GeneratedSerializer` gained a method 1.7 does not have. AGP's
    // consistent resolution forces the androidTest classpath to match the app's,
    // and the app was on 1.7.3 transitively via lifecycle -- so the test APK got
    // 1.7.3 and every migration test died on AbstractMethodError before running
    // an assertion. A constraint rather than a dependency: nothing here wants
    // serialization directly, this only raises the version of what already
    // arrives on its own.
    constraints {
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing needs the kotlinx-serialization 1.8 ABI on the androidTest classpath")
        }
    }

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    // Account deletion is an Edge Function call: removing an auth.users row
    // needs the secret key, which must never be in the APK. See #83.
    implementation(libs.supabase.functions)
    // Credential Manager called directly rather than through supabase-kt's
    // compose-auth, which is why that artifact is no longer a dependency: its
    // result type folds a missing Google account into the same "closed by user"
    // case as a real dismissal, and those two need opposite responses. See #93.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.google.identity.googleid)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.coil.compose)
    implementation(libs.coil)

    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)

    // The instrumented suite. Its whole point is to exercise what a JVM test
    // cannot: a real Activity, a real InputMethodService, and the accessibility
    // tree the platform actually builds. See #67 -- before this, the two
    // required emulator checks ran zero tests and reported success.
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
