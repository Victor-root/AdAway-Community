import java.util.Properties
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Builds are signed when keystore.properties exists at the project root, with the keys storeFile,
// storePassword, keyAlias and keyPassword. The key must stay the one used by the previous releases,
// or the app could not be updated in place. Debug builds use it too, so that they can be installed
// over a released version to test the update.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasSigningKey = signingKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }

// A pre-release names its stage after the app, as in "AdAway Alpha", for testers to tell it apart.
val appVersionName = libs.versions.appVersionName.get()
val preReleaseStage = mapOf("alpha" to "Alpha", "beta" to "Beta", "rc" to "RC").entries.firstOrNull { (word, _) ->
    Regex("[a-z]+").findAll(appVersionName.substringAfter('-', "").lowercase()).any { it.value == word }
}?.value

android {
    namespace = "org.adaway"
    compileSdk {
        version = release(libs.versions.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.compileSdkMinor.get().toInt()
        }
    }
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "org.adaway"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = appVersionName
        resValue("string", "app_name", listOfNotNull("AdAway", preReleaseStage).joinToString(" "))
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // Both build types are optimized the same way, to measure and feel the real speed of the app.
        // The debug one is no longer debuggable from Android Studio, but keeps its logs.
        all {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            if (hasSigningKey) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isDebuggable = false
            buildConfigField("boolean", "DEBUG", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // Below Android 13 the language is picked in the app, so every translation has to be installed.
    bundle {
        language {
            enableSplit = false
        }
    }

    // The root processes are run from the installed libraries: they must be extracted on install.
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

/**
 * Builds the Rust engine for every Android ABI with cargo-ndk, and the root processes next to it:
 * executables named like libraries, so that Android installs them with them. It is always built
 * optimized, as the debug build of the app is.
 */
abstract class CargoNdkTask @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal
    abstract val workspace: DirectoryProperty

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val abis = mapOf(
            "arm64-v8a" to "aarch64-linux-android",
            "armeabi-v7a" to "armv7-linux-androideabi",
            "x86" to "i686-linux-android",
            "x86_64" to "x86_64-linux-android",
        )
        val result = exec.exec {
            workingDir = workspace.get().asFile
            environment("ANDROID_NDK_HOME", ndkDirectory.get().asFile.absolutePath)
            commandLine(
                listOf("cargo", "ndk") + abis.keys.flatMap { listOf("-t", it) } +
                    listOf("--platform", minSdk.get().toString(), "-o", output.absolutePath, "build") +
                    listOf("--release", "-p", "adaway-android", "-p", "adaway-root"),
            )
            isIgnoreExitValue = true
        }
        if (result.exitValue != 0) {
            throw GradleException(
                "The Rust engine build failed. It needs rustup (https://rustup.rs) and cargo-ndk " +
                    "(cargo install cargo-ndk --locked), see the error above.",
            )
        }
        for ((abi, target) in abis) {
            for (executable in listOf("adaway-webserver", "adaway-capture")) {
                workspace.file("target/$target/release/$executable").get().asFile
                    .copyTo(File(output, "$abi/lib${executable.replace('-', '_')}.so"), overwrite = true)
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val rustWorkspace = rootProject.layout.projectDirectory.dir("rust")
        val cargoTask = tasks.register<CargoNdkTask>("buildRustEngine${variant.name.replaceFirstChar(Char::uppercase)}") {
            sources.from(fileTree(rustWorkspace) { exclude("target") })
            workspace.set(rustWorkspace)
            ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory)
            minSdk.set(variant.minSdk.apiLevel)
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoTask, CargoNdkTask::outputDirectory)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.libsu.core)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
