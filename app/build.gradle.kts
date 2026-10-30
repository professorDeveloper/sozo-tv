import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("com.google.gms.google-services")
}
val localProps = Properties()
val localPropsFile = project.rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

fun readLocalProperty(name: String, default: String = ""): String {
    val props = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { props.load(it) }
    }
    return props.getProperty(name).orEmpty().ifBlank { default }
}

// local.properties holds only sdk.dir on a fresh checkout, so every one of these MUST carry a
// working default — APISOZO_BASE_URL already compiles to "" for exactly that reason, which is
// why ApisozoClient ends up building relative URLs.
val sozoApiBaseUrl = readLocalProperty("SOZO_API_BASE_URL", "https://apisozo.azamov.me/api/")
val sozoLinkBaseUrl = readLocalProperty("SOZO_LINK_BASE_URL", "https://sozo.azamov.me/link/")

val keystorePropertiesFile = rootProject.file("key.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists() &&
        !keystoreProperties.getProperty("storeFile").isNullOrBlank()

android {
    namespace = "com.saikou.sozo_tv"
    compileSdk = 36
    kapt {
        correctErrorTypes = true
        useBuildCache = true
    }

    defaultConfig {

        applicationId = "com.saikou.sozo_tv"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 3
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            val token = readLocalProperty("GITHUB_TOKEN")
            buildConfigField("String", "GITHUB_TOKEN", "\"$token\"")
            buildConfigField(
                "String", "APISOZO_BASE_URL", "\"${readLocalProperty("APISOZO_BASE_URL", sozoApiBaseUrl)}\""
            )
            buildConfigField("String", "SOZO_API_BASE_URL", "\"$sozoApiBaseUrl\"")
            buildConfigField("String", "SOZO_LINK_BASE_URL", "\"$sozoLinkBaseUrl\"")

        }
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            buildConfigField("String", "GITHUB_TOKEN", "\"\"")
            buildConfigField(
                "String", "APISOZO_BASE_URL", "\"${readLocalProperty("APISOZO_BASE_URL", sozoApiBaseUrl)}\""
            )
            buildConfigField("String", "SOZO_API_BASE_URL", "\"$sozoApiBaseUrl\"")
            buildConfigField("String", "SOZO_LINK_BASE_URL", "\"$sozoLinkBaseUrl\"")

        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
        // Vendored Aniyomi OkHttpExtensions.parseAs uses context receivers.
        // Skip-metadata: the CloudStream `library` + serialization runtime are
        // may carry newer Kotlin metadata than this compiler — read their metadata.
        freeCompilerArgs += listOf("-Xcontext-receivers", "-Xskip-metadata-version-check")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Repo/plugin-list parsing logs what it rejected, and android.util.Log throws
            // "not mocked" off-device. Defaulting the stubs lets that code be tested without
            // the parsers having to stay silent about the entries they drop. org.json is NOT
            // left to the stub — see the real implementation in testImplementation below.
            isReturnDefaultValues = true
        }
    }
    // CloudStream's `library` pulls okhttp5 + jspecify etc., which collide on some
    // META-INF resources. Drop the duplicates so packaging succeeds.
    packaging {
        resources {
            excludes += setOf(
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }
}

// Keep plugin runtime ABI current and the Kotlin compiler/runtime aligned.
configurations.all {
    exclude(group = "com.github.recloudstream", module = "torrentserver")
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" &&
            (requested.name.startsWith("kotlin-stdlib") || requested.name == "kotlin-reflect")) {
            useVersion("2.2.20")
        }
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-coroutines")) {
            useVersion("1.11.0")
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("com.github.bumptech.glide:okhttp3-integration:4.15.1")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.3")
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.9")
    implementation("com.google.firebase:firebase-database:21.0.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    // Koin
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.navigation:navigation-runtime-ktx:2.8.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.8.5")
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.5")

    // Dots indicator
    implementation("com.tbuonomo:dotsindicator:5.1.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    //
    // qr
    implementation("com.google.zxing:core:3.5.2")

    // preference
    implementation("androidx.preference:preference-ktx:1.2.1")

    // Loopback HLS proxy server (LocalHlsProxy) — com.sun.net.httpserver isn't on Android.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    //REST - APIService
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // secure
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    implementation("de.hdodenhof:circleimageview:3.1.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")

    // exo player
    val media3_version = "1.9.2"

    implementation("androidx.media3:media3-transformer:$media3_version")
    implementation("androidx.media3:media3-exoplayer:$media3_version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3_version")
    implementation("androidx.media3:media3-ui:$media3_version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3_version")
    implementation("androidx.media3:media3-session:$media3_version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3_version")
    //Jackson
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")


    //FacebookShimmmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    implementation("androidx.activity:activity:1.10.0")

    /// Spinner
    implementation("com.github.skydoves:powerspinner:1.2.7")

    //Trailer Plugins
    implementation("com.github.Blatzar:NiceHttp:0.4.4")
    implementation("org.jsoup:jsoup:1.18.1")

    //
    //Room ORM
    // Room Components
    implementation("androidx.room:room-runtime:2.7.1")
    //noinspection KaptUsageInsteadOfKsp
    kapt("androidx.room:room-compiler:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation(
        "org.kamranzafar:jtar:2.0.1"
    )

    implementation("com.kongzue.dialogx:DialogX:0.0.49")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("com.bugsnag:bugsnag-android:6.+")
    implementation("com.bugsnag:bugsnag-android-performance:1.+")
    implementation("com.jakewharton.threetenabp:threetenabp:1.4.4")
    implementation("com.github.skydoves:androidribbon:1.0.4")
    implementation("com.github.skydoves:progressview:1.1.3")

    //MarkdownView
    implementation("io.noties.markwon:core:v4.6.2")

    val markwon_version = "4.6.2"
    implementation("io.noties.markwon:core:$markwon_version")
    implementation("io.noties.markwon:image:$markwon_version")
    implementation("io.noties.markwon:html:$markwon_version")
    implementation("io.noties.markwon:ext-strikethrough:$markwon_version")
    implementation("io.noties.markwon:inline-parser:$markwon_version")
    implementation("org.mozilla:rhino:1.7.13")
    implementation("io.noties.markwon:image:4.6.2")

    //Json
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // ---- Extension engine (Aniyomi .apk + CloudStream .cs3 runtime) ----
    // CloudStream provider runtime: MainAPI/APIHolder/`app` HTTP/extractors/BasePlugin.
    // Match the mobile plugin ABI, including getJson and DrmExtractorLink.
    implementation("com.github.recloudstream.cloudstream:library:v4.8.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation(files("libs/torrentserver-7861970-16k.aar"))
    // Plugins are compiled against the full CloudStream app, which carries Ktor; the
    // `library` artifact does not. Without it a plugin that touches io.ktor.http dies with
    // NoClassDefFoundError the moment load() runs (AllMovieLand, among others).
    implementation("io.ktor:ktor-http:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // compileOnly: our clean-room CloudflareKiller implements okhttp3.Interceptor;
    // okhttp itself is supplied at runtime by the CloudStream `library`.
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    // Aniyomi extension runtime supplied to DexClassLoader at load time.
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.2.20")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3")
    implementation("io.reactivex:rxjava:1.3.8")
    // JS engine for Aniyomi extractors that deobfuscate links (QuickJS).
    //
    // The published app.cash.quickjs:quickjs-android:0.9.2 with its native
    // library rebuilt 16 KB page-aligned (the same AAR as the phone app's
    // android/app/libs). Upstream libquickjs.so is 4 KB-aligned, and on a
    // 16 KB-page device dlopen refuses it outright. Same classes.jar, same
    // sources (cashapp/zipline a738129cc4), same JNI entry points; built with
    // NDK r28 and -Wl,-z,max-page-size=16384. Extensions compile against this
    // API, so it has to stay this library and version.
    implementation(files("libs/quickjs-android-0.9.2-16k.aar"))
    implementation("androidx.annotation:annotation:1.9.1")

    testImplementation("junit:junit:4.13.2")
    // The android.jar stub's org.json does nothing; the repo parsers are built on it, so the
    // unit tests need the real implementation ahead of the stub on their classpath.
    testImplementation("org.json:json:20250517")
}

// See the quickjs entry in dependencies: the 16 KB-aligned rebuild replaces it,
// so nothing may bring the published one back.
configurations.all {
    exclude(group = "app.cash.quickjs", module = "quickjs-android")
}
