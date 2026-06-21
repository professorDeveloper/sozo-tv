import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}
val localProps = Properties()
val localPropsFile = project.rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

fun readLocalProperty(name: String): String {
    val props = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { props.load(it) }
    }
    return props.getProperty(name, "")
}

android {
    namespace = "com.saikou.sozo_tv"
    compileSdk = 35
    kapt {
        correctErrorTypes = true
        useBuildCache = true
    }

    defaultConfig {

        applicationId = "com.saikou.sozo_tv"
        minSdk = 24
        //noinspection OldTargetApi
        targetSdk = 35
        versionCode = 9
        versionName = "9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {}
        }

    }
    buildTypes {
        debug {
            val token = readLocalProperty("GITHUB_TOKEN")
            buildConfigField("String", "GITHUB_TOKEN", "\"$token\"")

        }
        release {
            //
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            buildConfigField("String", "GITHUB_TOKEN", "\"\"")

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
        // built with a newer Kotlin than this module (1.9) — read their metadata.
        freeCompilerArgs += listOf("-Xcontext-receivers", "-Xskip-metadata-version-check")
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
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

// The CloudStream runtime drags kotlin-stdlib up to 2.3.0 (metadata 2.3.0), which
// the Room/kapt annotation processor can't read (and which is newer than this
// module's Kotlin 1.9 compiler). Pin the whole kotlin-stdlib family back to 1.9.x
// so metadata stays readable and the toolchain stays consistent.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" &&
            requested.name.startsWith("kotlin-stdlib")
        ) {
            useVersion("1.9.24")
        }
        // Keep coroutines at 1.7.3 — 1.8.x's @InternalForInheritanceCoroutinesApi
        // breaks the Kotlin 1.9.0 compiler backend.
        if (requested.group == "org.jetbrains.kotlinx" &&
            requested.name.startsWith("kotlinx-coroutines")
        ) {
            useVersion("1.7.3")
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
    implementation("com.google.ai.edge.litert:litert-support-api:1.4.0")
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

    // DI
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")


    //
    // qr
    implementation("com.google.zxing:core:3.5.2")

    // websocket
    implementation("org.java-websocket:Java-WebSocket:1.5.3")

    // preference
    implementation("androidx.preference:preference-ktx:1.2.1")

    //REST - APIService
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
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


    //Exoplayer2
    implementation("com.google.android.exoplayer:exoplayer-core:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-ui:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-dash:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-hls:2.18.7")
    implementation("com.google.android.exoplayer:exoplayer-smoothstreaming:2.18.7")


    //Blur
    implementation("jp.wasabeef:glide-transformations:4.3.0")

    //FacebookShimmmer
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    //Jwt Token
    implementation("com.auth0.android:jwtdecode:2.0.0")
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

    val dialogx_version = "0.0.49"
    implementation("com.kongzue.dialogx:DialogX:${dialogx_version}")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("com.bugsnag:bugsnag-android:6.+")
    implementation("com.github.skydoves:balloon:1.6.0")
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
    implementation("com.github.recloudstream.cloudstream:library:v4.7.0")
    // CloudStream plugins/extractors use coroutines on the IO dispatcher.
    // 1.7.3 (not 1.8.x): 1.8.x adds @InternalForInheritanceCoroutinesApi which
    // trips the Kotlin 1.9.0 compiler backend.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // compileOnly: our clean-room CloudflareKiller implements okhttp3.Interceptor;
    // okhttp itself is supplied at runtime by the CloudStream `library`.
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    // Aniyomi extension runtime supplied to DexClassLoader at load time.
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.7.3")
    implementation("io.reactivex:rxjava:1.3.8")
    // JS engine for Aniyomi extractors that deobfuscate links (QuickJS).
    implementation("app.cash.quickjs:quickjs-android:0.9.2")
}