import com.paperplane.semver.Semver
import com.paperplane.semver.SemverResolver
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun prop(name: String, default: String): String =
    (project.findProperty(name) as String?) ?: default

fun git(vararg args: String): Pair<Int, String> {
    return try {
        val process = ProcessBuilder(listOf("git") + args.toList())
            .directory(rootProject.projectDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() to output
    } catch (_: Exception) {
        -1 to ""
    }
}

fun gitOutput(vararg args: String): String? {
    val (code, output) = git(*args)
    return if (code == 0) output.trim() else null
}

fun configuredVersion(propertyName: String, envName: String): String? {
    val property = (project.findProperty(propertyName) as String?)?.trim()?.takeIf { it.isNotEmpty() }
    val env = System.getenv(envName)?.trim()?.takeIf { it.isNotEmpty() }
    return property ?: env
}

fun resolveAppVersion(): com.paperplane.semver.ResolvedVersion {
    val nameOverride = configuredVersion("paperplane.versionName", "PAPERPLANE_VERSION_NAME")
    val codeOverride = configuredVersion("paperplane.versionCode", "PAPERPLANE_VERSION_CODE")?.toIntOrNull()

    if (!rootProject.file(".git").exists()) {
        logger.warn("No git metadata; PaperPlane version falls back to 1.0.0")
        return SemverResolver.resolve(
            base = null,
            headIsRelease = false,
            commitMessages = emptyList(),
            distance = 0,
            commitCount = 1,
            versionNameOverride = nameOverride,
            versionCodeOverride = codeOverride
        )
    }

    if (gitOutput("rev-parse", "--is-shallow-repository") == "true") {
        logger.warn("Shallow git checkout. Fetch the full history and tags so Semver and versionCode stay correct.")
    }

    val head = gitOutput("rev-parse", "HEAD")
    val commitCount = gitOutput("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 0
    val mergedTags = gitOutput("tag", "--merged", "HEAD")
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toList()
        .orEmpty()
    val baseName = SemverResolver.highestStableTag(mergedTags)
    val base = baseName?.let(Semver::parseTag)
    val baseCommit = baseName?.let { gitOutput("rev-list", "-n", "1", it) }
    val headIsRelease = !head.isNullOrBlank() && head == baseCommit
    val distance = when {
        baseName == null -> commitCount
        headIsRelease -> 0
        else -> gitOutput("rev-list", "--count", "$baseName..HEAD")?.toIntOrNull() ?: 0
    }
    val rawLog = when {
        headIsRelease || head.isNullOrBlank() -> null
        baseName == null -> gitOutput("log", "--format=%x1e%B")
        else -> gitOutput("log", "$baseName..HEAD", "--format=%x1e%B")
    }
    val messages = rawLog
        ?.split('\u001e')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()

    return SemverResolver.resolve(
        base = base,
        headIsRelease = headIsRelease,
        commitMessages = messages,
        distance = distance,
        commitCount = commitCount,
        versionNameOverride = nameOverride,
        versionCodeOverride = codeOverride
    ).also { resolved ->
        logger.lifecycle("PaperPlane version ${resolved.name} (${resolved.code})")
    }
}

val resolvedVersion = resolveAppVersion()

android {
    namespace = "com.barontech.paperplane"
    compileSdk = prop("compileSdkVersion", "35").toInt()
    buildToolsVersion = prop("buildToolsVersion", "35.0.0")

    defaultConfig {
        applicationId = "com.barontech.paperplane"
        minSdk = prop("minSdkVersion", "26").toInt()
        targetSdk = prop("targetSdkVersion", "35").toInt()
        versionCode = resolvedVersion.code
        versionName = resolvedVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "APPLICATION_ID", "\"${applicationId}\"")
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_FILE")
                ?: localProperties.getProperty("KEYSTORE_FILE")
            val storePasswordValue = System.getenv("KEYSTORE_PASSWORD")
                ?: localProperties.getProperty("KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("KEY_ALIAS")
                ?: localProperties.getProperty("KEY_ALIAS")
            val keyPasswordValue = System.getenv("KEY_PASSWORD")
                ?: localProperties.getProperty("KEY_PASSWORD")

            if (!storeFilePath.isNullOrBlank() &&
                !storePasswordValue.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank()
            ) {
                storeFile = file(storeFilePath)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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
}

tasks.register("printVersion") {
    group = "versioning"
    description = "Prints the Semver versionName and versionCode for this build."
    doLast {
        println("versionName=${resolvedVersion.name}")
        println("versionCode=${resolvedVersion.code}")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-messaging")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
