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

    // File name to SHA-256
    @get:Input abstract val files: MapProperty<String, String>

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun download() {
        val dir = outputDir.get().asFile
        for ((name, sha256) in files.get()) {
            val target = dir.resolve(name)
            if (target.isFile && sha256Of(target.readBytes()) == sha256) continue
            val url = baseUrl.get() + name
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
    namespace = "de.example.saxtuner"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "de.example.saxtuner"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }
}

ktlint {
    version = libs.versions.ktlint
}

// Real alto saxophone recordings used by SampleValidationTest; not part of the app or the repository.
// Source: University of Iowa Electronic Music Studios, Musical Instrument Samples
// https://theremin.music.uiowa.edu/MIS-Pitches-2012/MISEbAltoSaxophone2012.html
val downloadSamples = tasks.register<DownloadSamples>("downloadSamples") {
    baseUrl = "https://theremin.music.uiowa.edu/sound%20files/MIS%20Pitches%20-%202014/Woodwinds/Eb%20Alto%20Saxophone/"
    files = mapOf(
        "AltoSax.NoVib.ff.Db3.stereo.aif" to "22cf0a09d6ffef7988a3ce761fcf0b2e3f3d568e94fe9fa94aa291354546df83",
        "AltoSax.NoVib.ff.D3.stereo.aif" to "7b9701e53e0ef9c711542bdee1642e54778b3ac1e82294751ddc8c750d9eb69a",
        "AltoSax.NoVib.ff.C5.stereo.aif" to "0b4c5a57d75c1f35f5e5a30dd83a3af64629dc36e49e2f7531a95338cf3d2422",
        "AltoSax.vib.ff.F5.stereo.aif" to "137921200cb7ccfa656f96fc0119157ddc47624bf8c037f815c68c888dddcf8c"
    )
    // Gradle user home survives `clean` and is shared between checkouts
    outputDir = File(gradle.gradleUserHomeDir, "caches/saxtuner-samples/alto-saxophone")
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
