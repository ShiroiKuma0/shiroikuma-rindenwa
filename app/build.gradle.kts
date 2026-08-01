import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsPlugin
import com.google.gms.googleservices.GoogleServicesPlugin
import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kapt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.navigation)
}

// shiroikuma fork: the INSTALLED package id. The code namespace stays "org.linphone"
// (see android.namespace below) — renaming it would make every upstream rebase a mass-conflict.
// This drives the FileProvider authority, the AppAuth redirect scheme and the OpenID callback
// scheme too, so the fork installs side-by-side with stock Linphone.
val packageName = "shiroikuma.rindenwa"
val useDifferentPackageNameForDebugBuild = false

// shiroikuma fork: our per-build increment, bumped by the buildApk task, reset to 1 on each new
// upstream version. Applied to upstream's own versionCode/versionName below the defaultConfig
// block, so upstream's two literals stay byte-identical and never conflict on rebase.
val shiroikumaBuild = (providers.gradleProperty("BUILD_NUMBER").orNull ?: "1").toInt()

val sdkPath = providers.gradleProperty("LinphoneSdkBuildDir").get()
val googleServices = File(projectDir.absolutePath + "/google-services.json")
val linphoneLibs = File("$sdkPath/libs/")
val linphoneDebugLibs = File("$sdkPath/libs-debug/")
val firebaseCloudMessagingAvailable = googleServices.exists()
val crashlyticsAvailable = googleServices.exists() && linphoneLibs.exists() && linphoneDebugLibs.exists()

if (firebaseCloudMessagingAvailable) {
    println("google-services.json found, enabling Firebase CloudMessaging feature")
    apply<GoogleServicesPlugin>()
} else {
    println("google-services.json not found, disabling Firebase CloudMessaging feature")
}
if (crashlyticsAvailable) {
    println("google-services.json found and Linphone SDK libs-debug folder found, enabling Crashlytics feature")
    apply<CrashlyticsPlugin>()
} else {
    println("Crashlytics has been disabled because either google-services.json file wasn't found or local Linphone SDK build folder isn't configured")
}

var gitVersion = "6.3.0-alpha"
var gitBranch = ""
try {
    val gitDescribe = ProcessBuilder()
        .command("git", "describe", "--abbrev=0")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git describe: $gitDescribe")

    val gitCommitsCount = ProcessBuilder()
        .command("git", "rev-list", "$gitDescribe..HEAD", "--count")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git commits count: $gitCommitsCount")

    val gitCommitHash = ProcessBuilder()
        .command("git", "rev-parse", "--short", "HEAD")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git commit hash: $gitCommitHash")

    gitBranch = ProcessBuilder()
        .command("git", "name-rev", "--name-only", "HEAD")
        .directory(project.rootDir)
        .start()
        .inputStream.bufferedReader().use(BufferedReader::readText)
        .trim()
    println("Git branch name: $gitBranch")

    gitVersion =
        if (gitCommitsCount.toInt() == 0) {
            gitDescribe
        } else {
            "$gitDescribe.$gitCommitsCount+$gitCommitHash"
        }
} catch (e: Exception) {
    println("Git not found [$e], using $gitVersion")
}
println("Computed git version: $gitVersion")

configurations {
    implementation { isCanBeResolved = true }
}

tasks.register("linphoneSdkSource") {
    doLast {
        configurations.implementation.get().incoming.resolutionResult.allComponents.forEach {
            if (it.id.displayName.contains("linphone-sdk-android")) {
                println("Linphone SDK used is ${it.moduleVersion?.version}")
            }
        }
    }
}
project.tasks.preBuild.dependsOn("linphoneSdkSource")

android {
    namespace = "org.linphone"
    compileSdk = 37

    defaultConfig {
        applicationId = packageName
        minSdk = 28
        targetSdk = 37
        versionCode = 602005 // 6.02.005
        versionName = "6.3.0-alpha"

        manifestPlaceholders["appAuthRedirectScheme"] = packageName

        ndk {
            //noinspection ChromeOsAbiSupport
            // shiroikuma fork: single-ABI build (matches the shiroikuma-rindenwa_*_arm64-v8a.apk name).
            abiFilters += listOf("arm64-v8a")
        }
    }

    // shiroikuma fork versioning. Upstream's versionCode/versionName literals above are left exactly
    // as upstream writes them and read back here, so an upstream bump flows through untouched:
    //   versionName = "<upstream>.<upstream base date>.g<upstream base sha>+<BUILD_NUMBER, 3 digits>"
    //   versionCode = <upstream> * 1000 + <BUILD_NUMBER>
    // The x1000 tail (not x10000 as in the sister forks) is forced by Linphone's large upstream code:
    // 602003 * 10000 would overflow Android's 2100000000 versionCode ceiling.
    val upstreamVersionCode = defaultConfig.versionCode!!
    val upstreamVersionName = defaultConfig.versionName!!

    fun gitOutput(vararg command: String): String = try {
        ProcessBuilder()
            .command(*command)
            .directory(project.rootDir)
            .start()
            .inputStream.bufferedReader().use(BufferedReader::readText)
            .trim()
    } catch (e: Exception) {
        println("Git command [${command.joinToString(" ")}] failed [$e]")
        ""
    }

    // shiroikuma fork: upstream-base pin (global "git-versioning" skill). We rebase `custom` onto
    // every upstream commit, so upstream's versionName stands still for months — this pin is what
    // says whether we are behind upstream. It is the merge-base of HEAD and `master` (the upstream
    // mirror), i.e. the upstream commit our patches sit on: NOT our own HEAD (that identifies our
    // commits, which +BUILD_NUMBER already covers) and NOT master's tip (which overstates the base
    // when master has been fast-forwarded but custom not yet rebased). A missing sha — shallow
    // clone, tarball, no git — must never fail the build; the version just degrades to <upstream>+N.
    val upstreamBaseSha = gitOutput("git", "merge-base", "HEAD", "master").take(8)

    // The date of that same commit, so versions sort chronologically. A bare sha is random text:
    // g5c0ed6a3 (newer) sorts before g6441c21e (older), which puts the newest APK in the middle of
    // the file manager's list. The date is the upstream commit's own committer date, NOT build time,
    // so every build on one upstream base keeps an identical pin.
    val upstreamBaseDate = if (upstreamBaseSha.length == 8) {
        gitOutput("git", "show", "-s", "--format=%cd", "--date=format:%Y-%m-%d", upstreamBaseSha)
    } else {
        ""
    }

    val upstreamPin = when {
        upstreamBaseSha.length != 8 -> ""
        upstreamBaseDate.length == 10 -> ".$upstreamBaseDate.g$upstreamBaseSha"
        else -> ".g$upstreamBaseSha"
    }
    println("Upstream base pin: ${if (upstreamPin.isEmpty()) "(none)" else upstreamPin}")

    // Zero-padded to 3 digits so +002 sorts before +010 under a plain lexicographic sort (a file
    // manager that sorts by name would otherwise read "+10" as earlier than "+3"). Name only —
    // versionCode below stays a plain integer. Three digits is also the ceiling the x1000 tail
    // allows: BUILD_NUMBER must stay under 1000 or forkVersionCode would collide with the next
    // upstream version's range.
    val paddedBuild = shiroikumaBuild.toString().padStart(3, '0')

    val forkVersionName = "$upstreamVersionName$upstreamPin+$paddedBuild"
    val forkVersionCode = upstreamVersionCode * 1000 + shiroikumaBuild
    defaultConfig.versionCode = forkVersionCode
    defaultConfig.versionName = forkVersionName
    println("shiroikuma fork version: $forkVersionName (versionCode $forkVersionCode)")

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                // shiroikuma fork: house APK naming, e.g. shiroikuma-rindenwa_6.3.0-alpha+1_arm64-v8a.apk
                output.outputFileName =
                    if (variant.buildType.name == "release") {
                        "shiroikuma-rindenwa_${forkVersionName}_arm64-v8a.apk"
                    } else {
                        "shiroikuma-rindenwa_$forkVersionName-${variant.buildType.name}_arm64-v8a.apk"
                    }
            }
    }

    // shiroikuma fork: our signing credentials live OUTSIDE the repo, in
    // ~/.gradle/gradle.properties, as RINDENWA_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS /
    // _KEY_PASSWORD. Upstream tracks keystore.properties and our `custom` branch deletes it, so a
    // `git checkout master` during an upstream sync overwrote our real file with upstream's empty
    // one and the switch back then deleted it — silently losing the signing password, with no way
    // to recover it from git (it is gitignored). A Gradle property leaves nothing in the working
    // tree for a branch switch to clobber. See CLAUDE.md → "Build, versioning, signing".
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    // Still honoured when present, so upstream's own GitLab CI (which writes this file from CI
    // secrets) keeps working; our builds never rely on it.
    if (keystorePropertiesFile.exists()) {
        keystoreProperties.load(FileInputStream(keystorePropertiesFile))
    }

    // Gradle property first, keystore.properties second, null when neither supplies a value.
    fun signingSetting(gradleProperty: String, fileKey: String): String? =
        (providers.gradleProperty(gradleProperty).orNull ?: keystoreProperties[fileKey] as String?)
            ?.takeIf { it.isNotBlank() }

    val releaseStorePath = signingSetting("RINDENWA_RELEASE_STORE_FILE", "storeFile") ?: ""
    val releaseStorePassword = signingSetting("RINDENWA_RELEASE_STORE_PASSWORD", "storePassword")
    val releaseKeyAlias = signingSetting("RINDENWA_RELEASE_KEY_ALIAS", "keyAlias")
    val releaseKeyPassword =
        signingSetting("RINDENWA_RELEASE_KEY_PASSWORD", "keyPassword") ?: releaseStorePassword

    signingConfigs {
        create("release") {
            // shiroikuma fork: empty path when nothing is configured — configuration must not fail.
            val keyStore = project.file(releaseStorePath.ifEmpty { "keystore-not-configured" })
            if (keyStore.exists() &&
                releaseStorePassword != null &&
                releaseKeyAlias != null &&
                releaseKeyPassword != null
            ) {
                storeFile = keyStore
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                println("Signing config release is using keystore [$storeFile]")
            } else {
                println(
                    "Signing is not configured — set RINDENWA_RELEASE_* in " +
                        "~/.gradle/gradle.properties (see CLAUDE.md). Release builds will be unsigned!"
                )
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (useDifferentPackageNameForDebugBuild) {
                applicationIdSuffix = ".debug"
            }
            isDebuggable = true
            isJniDebuggable = true

            val appVersion = gitVersion
            val appBranch = gitBranch
            println("Debug flavor app version is [$appVersion], app branch is [$appBranch]")
            resValue("string", "linphone_app_version", appVersion)
            resValue("string", "linphone_app_branch", appBranch)
            if (useDifferentPackageNameForDebugBuild) {
                resValue("string", "file_provider", "$packageName.debug.fileprovider")
            } else {
                resValue("string", "file_provider", "$packageName.fileprovider")
            }
            resValue("string", "linphone_openid_callback_scheme", packageName)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }

        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")

            val appVersion = gitVersion
            val appBranch = gitBranch
            println("Release flavor app version is [$appVersion], app branch is [$appBranch]")
            resValue("string", "linphone_app_version", appVersion)
            resValue("string", "linphone_app_branch", appBranch)
            resValue("string", "file_provider", "$packageName.fileprovider")
            resValue("string", "linphone_openid_callback_scheme", packageName)

            if (crashlyticsAvailable) {
                val path = File("$sdkPath/libs-debug/").toString()
                configure<CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    unstrippedNativeLibsDir = path
                }
            }
            buildConfigField("Boolean", "CRASHLYTICS_ENABLED", crashlyticsAvailable.toString())
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        dataBinding = true
        // shiroikuma fork: the 白い熊 臨電話 UI layer builds its rows from plain (non-<layout>)
        // XML, which data binding alone does not generate binding classes for.
        viewBinding = true
        buildConfig = true
        resValues = true
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.annotations)
    // shiroikuma fork: SAF tree handling for the 白い熊 臨電話 Export/Import layer.
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraint.layout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.telecom)
    implementation(libs.androidx.media)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.slidingpanelayout)
    implementation(libs.androidx.window)
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.security.crypto.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.emoji2)
    implementation(libs.androidx.car)

    // https://github.com/google/flexbox-layout/blob/main/LICENSE Apache v2.0
    implementation(libs.google.flexbox)
    // https://github.com/material-components/material-components-android/blob/master/LICENSE Apache v2.0
    implementation(libs.google.material)
    // To be able to parse native crash tombstone and print them with SDK logs the next time the app will start
    implementation(libs.google.protobuf)

    implementation(platform(libs.google.firebase.bom))
    implementation(libs.google.firebase.messaging)
    if (crashlyticsAvailable) {
        implementation(libs.google.firebase.crashlytics)
    } else {
        compileOnly(libs.google.firebase.crashlytics)
    }

    // https://github.com/coil-kt/coil/blob/main/LICENSE.txt Apache v2.0
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    // https://github.com/tommybuonomo/dotsindicator/blob/master/LICENSE Apache v2.0
    implementation(libs.dots.indicator)
    // https://github.com/Baseflow/PhotoView/blob/master/LICENSE Apache v2.0
    implementation(libs.photoview)
    // https://github.com/openid/AppAuth-Android/blob/master/LICENSE Apache v2.0
    implementation(libs.openid.appauth)

    implementation(libs.linphone)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}

configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    android.set(true)
    ignoreFailures.set(true)
    additionalEditorconfig.set(
        mapOf(
            "max_line_length" to "120",
            "ktlint_standard_max-line-length" to "disabled",
            "ktlint_standard_function-signature" to "disabled",
            "ktlint_standard_no-blank-line-before-rbrace" to "disabled",
            "ktlint_standard_no-empty-class-body" to "disabled",
            "ktlint_standard_annotation-spacing" to "disabled",
            "ktlint_standard_class-signature" to "disabled",
            "ktlint_standard_function-expression-body" to "disabled",
            "ktlint_standard_function-type-modifier-spacing" to "disabled",
            "ktlint_standard_if-else-wrapping" to "disabled",
            "ktlint_standard_argument-list-wrapping" to "disabled",
            "ktlint_standard_trailing-comma-on-call-site" to "disabled",
            "ktlint_standard_trailing-comma-on-declaration-site" to "disabled",
            "ktlint_standard_no-empty-first-line-in-class-body" to "disabled",
            "ktlint_standard_no-empty-first-line-in-method-block" to "disabled",
            "ktlint_standard_no-trailing-spaces" to "disabled",
            "ktlint_standard_no-blank-line-in-list" to "disabled",
            "ktlint_standard_no-multi-spaces" to "disabled",
            "ktlint_standard_try-catch-finally-spacing" to "disabled",
            "ktlint_standard_block-comment-initial-star-alignment" to "disabled",
            "ktlint_standard_spacing-between-declarations-with-comments" to "disabled",
            "ktlint_standard_no-consecutive-comments" to "disabled",
            "ktlint_standard_multiline-expression-wrapping" to "disabled",
            "ktlint_standard_parameter-list-wrapping" to "disabled",
            "ktlint_standard_comment-wrapping" to "disabled",
            "ktlint_standard_discouraged-comment-location" to "disabled",
            "ktlint_standard_string-template-indent" to "disabled",
            "ktlint_standard_parameter-list-spacing" to "disabled",
            "ktlint_standard_statement-wrapping" to "disabled",
            "ktlint_standard_import-ordering" to "disabled",
            "ktlint_standard_paren-spacing" to "disabled",
            "ktlint_standard_curly-spacing" to "disabled",
            "ktlint_standard_indent" to "disabled",
        )
    )
}
project.tasks.preBuild.dependsOn("ktlintFormat")

if (crashlyticsAvailable) {
    afterEvaluate {
        tasks.getByName("assembleDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("packageDebug").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileDebug"),
        )
        tasks.getByName("assembleRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
        tasks.getByName("packageRelease").finalizedBy(
            tasks.getByName("uploadCrashlyticsSymbolFileRelease"),
        )
    }
}

// --- shiroikuma fork: build the signed release APK, copy it to ~/tmp, bump BUILD_NUMBER ---
tasks.register("buildApk") {
    description = "Build the signed release APK, copy it to ~/tmp, and bump BUILD_NUMBER for next time."
    group = "build"
    dependsOn("assembleRelease")
    // Capture project state at configuration time so the action is configuration-cache compatible.
    val fvName = android.defaultConfig.versionName
    val fvCode = android.defaultConfig.versionCode
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/release")
    val userHome = providers.systemProperty("user.home")
    val propsFile = rootProject.file("gradle.properties")
    val currentBuildNumber = shiroikumaBuild
    doLast {
        val apkName = "shiroikuma-rindenwa_${fvName}_arm64-v8a.apk"
        val outputDir = releaseApkDir.get().asFile
        val targetDir = File(userHome.get(), "tmp")
        targetDir.mkdirs()
        outputDir.listFiles { _, name -> name.endsWith(".apk") }?.firstOrNull()?.let { apk ->
            val targetFile = File(targetDir, apkName)
            apk.copyTo(targetFile, overwrite = true)
            println("\u001B[1;36m>>> ${targetFile.absolutePath}\u001B[0m")
            println("\u001B[1;36m>>> versionCode $fvCode\u001B[0m")
        } ?: throw GradleException("No APK found in $outputDir")

        // Auto-increment BUILD_NUMBER for the next build.
        val nextBuildNumber = currentBuildNumber + 1
        propsFile.writeText(
            propsFile.readText().replace(
                "BUILD_NUMBER=$currentBuildNumber",
                "BUILD_NUMBER=$nextBuildNumber"
            )
        )
        println("\u001B[1;36m>>> BUILD_NUMBER bumped to $nextBuildNumber\u001B[0m")
    }
}
