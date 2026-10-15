import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
}

val appVersionCode = 46
val baseVersionName = "0.16.1"

// Как в AtlasCodecFix: вне main к версии и имени APK дописывается ветка.
fun currentGitBranch(): String {
    return runCatching {
        val process = ProcessBuilder("git", "branch", "--show-current")
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val branch = process.inputStream.bufferedReader().use { it.readText().trim() }
        process.waitFor()
        branch
    }.getOrDefault("")
}

val buildBranch = providers.gradleProperty("branch.name").orNull
    ?: System.getenv("GIT_BRANCH")
        ?.removePrefix("refs/heads/")
        ?.removePrefix("refs/remotes/")
        ?.removePrefix("origin/")
    ?: currentGitBranch()
val sanitizedBranch = buildBranch
    .ifBlank { "detached" }
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-')
    .lowercase(Locale.ROOT)
    .take(32)
    .ifBlank { "detached" }
val appVersionName = if (buildBranch == "main") {
    baseVersionName
} else {
    "$baseVersionName-$sanitizedBranch"
}

// Штатный общедоступный пароль root QNX ГУ G636; переопределяется qnx.password
// в local.properties или QNX_PASS.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val qnxPassword = localProps.getProperty("qnx.password") ?: System.getenv("QNX_PASS") ?: "fGh4dalvHp4ubmb2"
// Подпись релиза общим ключом Atlas: secure.signing в gradle.properties указывает
// на игнорируемый secure.signing.gradle. Без него release не собирается.
val secureSigningScript = providers.gradleProperty("secure.signing").orNull?.let(rootProject::file)
val hasReleaseSigning = secureSigningScript?.isFile == true

fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

base {
    archivesName.set("${appVersionName}[${appVersionCode}]AtlasDim")
}

android {
    namespace = "com.mmwtl.atlasdim"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mmwtl.atlasdim"
        minSdk = 26
        targetSdk = 30
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "com.mmwtl.atlasdim.SceneTestRunner"
        buildConfigField("String", "QNX_HOST", quoted("192.168.118.2"))
        buildConfigField("String", "QNX_PASSWORD", quoted(qnxPassword))
    }

    buildFeatures {
        buildConfig = true
    }

    // Демон для QNX (qnx/ghudbridgelited-dim) кладётся в assets; скрипты — нет.
    sourceSets["main"].assets.srcDir(rootProject.file("qnx"))
    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~:!*.sh"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Исследовательский APK для OneOS, не Google Play; targetSdk=30 закреплён AGENTS.md.
        disable += "ExpiredTargetSdkVersion"
    }

    if (hasReleaseSigning) {
        apply(from = secureSigningScript!!)
    }
}

if (!hasReleaseSigning) {
    tasks.configureEach {
        if (name == "packageRelease" || name == "bundleRelease") {
            doFirst {
                throw GradleException("Release signing is required. Configure secure.signing.gradle.")
            }
        }
    }
}
