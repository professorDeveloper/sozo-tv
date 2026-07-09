# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JNI orqali chaqiriladigan klassni himoya qilamiz
-keep class com.saikou.sozo_tv.utils.Security {
    public *;
}

#-keep  com.saikou.sozo_tv
# Barcha native methodlarni saqlaymiz
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---------------------------------------------------------------------------
# NOTE: release currently ships with `isMinifyEnabled` unset, so nothing below
# is applied yet. These rules exist so that turning R8 on does not silently
# reintroduce release-only breakage — every mechanism guarded here resolves
# names at runtime and is therefore invisible to the shrinker.
# ---------------------------------------------------------------------------

# Stack traces reported to Bugsnag stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic signatures + annotations: Gson, Retrofit and Firebase all read them.
-keepattributes Signature,*Annotation*,Exceptions,InnerClasses,EnclosingMethod

# Firebase Realtime Database maps snapshots onto these POJOs by field name, and
# Gson/Retrofit deserialize the same packages. Renaming a field silently yields
# an all-defaults object rather than an error.
-keep class com.saikou.sozo_tv.domain.model.** { *; }
-keep class com.saikou.sozo_tv.data.model.** { *; }
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Gson type tokens carry their type argument only in the generic signature.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retrofit service interfaces are implemented by a runtime proxy.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# kotlinx.serialization looks up the generated $serializer by name.
-keepclassmembers class **$$serializer { *** descriptor; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.saikou.sozo_tv.**$$serializer { *; }
-keepclassmembers class com.saikou.sozo_tv.** {
    *** Companion;
}

# Extension plugins are dex files loaded at runtime with PathClassLoader; their
# code links against these host classes by name and R8 cannot see those edges.
-keep class com.lagradost.** { *; }
-keep class eu.kanade.** { *; }
-keep class app.cash.quickjs.** { *; }
-dontwarn com.lagradost.**
-dontwarn eu.kanade.**

# kotlin-reflect is used to instantiate Aniyomi sources.
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
