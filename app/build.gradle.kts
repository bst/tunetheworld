import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
}

/** Downloads the test recordings into a cache directory, skipping files that are already present and intact. */
abstract class DownloadSamples : DefaultTask() {
    @get:Input abstract val baseUrl: Property<String>

    // Path below baseUrl to SHA-256; files are stored flat under their file name
    @get:Input abstract val files: MapProperty<String, String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile
        for ((path, sha256) in files.get()) {
            val name = path.substringAfterLast('/')
            val target = dir.resolve(name)
            if (target.isFile && sha256Of(target.readBytes()) == sha256) continue
            val url = baseUrl.get() + path.replace(" ", "%20")
            logger.lifecycle("Downloading $url")
            val bytes = URI(url).toURL().openStream().use { it.readBytes() }
            val actual = sha256Of(bytes)
            check(actual == sha256) { "Checksum mismatch for $name: expected $sha256, got $actual" }
            val tmp = dir.resolve("$name.part")
            tmp.writeBytes(bytes)
            check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "Could not write $target" }
        }
    }

    private fun sha256Of(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

android {
    namespace = "de.example.tunetheworld"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.example.tunetheworld"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // Release signing key from Gradle properties, e.g. in ~/.gradle/gradle.properties (never in the repo).
    // Without them, release builds are signed with the debug key so they can still be installed locally.
    val releaseStoreFile = providers.gradleProperty("tunetheworld.release.storeFile").orNull
    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = providers.gradleProperty("tunetheworld.release.storePassword").get()
                keyAlias = providers.gradleProperty("tunetheworld.release.keyAlias").get()
                keyPassword = providers.gradleProperty("tunetheworld.release.keyPassword").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        warningsAsErrors = true
        // Warnings that existed when warningsAsErrors was switched on; new ones fail the build
        baseline = file("lint-baseline.xml")
        // These depend on what was released upstream today, not on the code, so they would break builds at random.
        // Dependency updates are handled separately (see PLAN.md).
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
    }
}

ktlint {
    version = libs.versions.ktlint
}

// Real instrument recordings used by SampleValidationTest; not part of the app or the repository.
// The list, with checksums, is in src/test/resources/samples.tsv.
// Source: University of Iowa Electronic Music Studios, Musical Instrument Samples
// https://theremin.music.uiowa.edu/MIS.html
val downloadSamples = tasks.register<DownloadSamples>("downloadSamples") {
    val manifest = file("src/test/resources/samples.tsv")
    baseUrl = "https://theremin.music.uiowa.edu/sound%20files/"
    files = manifest.readLines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .associate { line -> line.split('\t').let { it[0] to it[1] } }
    // Gradle user home survives `clean` and is shared between checkouts
    outputDir = File(gradle.gradleUserHomeDir, "caches/tunetheworld-samples/iowa-mis")
}

tasks.withType<Test>().configureEach {
    dependsOn(downloadSamples)
    systemProperty("samples.dir", downloadSamples.get().outputDir.get().asFile.absolutePath)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
