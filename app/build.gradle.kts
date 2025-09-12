plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
//    id("kotlinx-serialization")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlin-kapt")
    id("com.apollographql.apollo3") version "3.7.0"
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")

    //
}
android {
    namespace = "com.saikou.sozo_tv"
    compileSdk = 35
    kapt {
        correctErrorTypes = true
    }

    apollo {
        packageName.set("com.animestudios.animeapp")
        generateKotlinModels.set(true)
        excludes.add("**/schema.json.graphql")
    }
    defaultConfig {
        applicationId = "com.saikou.sozo_tv"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
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

    // DI
    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-compiler:2.48")


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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // exo player
    implementation("androidx.media3:media3-exoplayer:1.0.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.0.0")
    implementation("androidx.media3:media3-ui:1.0.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.0.0")
    implementation("androidx.media3:media3-session:1.0.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.0.0")

    //Jackson
    api ("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")


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
    implementation("org.jsoup:jsoup:1.15.1")

    //
    //Room ORM
    // Room Components
    implementation("androidx.room:room-runtime:2.6.1")
    //noinspection KaptUsageInsteadOfKsp
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
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


    //GraphQL
    val apolloVersion = "3.7.0"
    implementation("com.apollographql.apollo3:apollo-runtime:$apolloVersion")

}