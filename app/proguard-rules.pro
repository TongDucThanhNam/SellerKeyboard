# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ──────────────────────────────────────────────
# Debugging: keep source file names and line numbers in stack traces
# ──────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────────
# Gson 2.x
# Gson uses reflection on generic types at runtime via TypeToken.
# ──────────────────────────────────────────────
-keepattributes Signature

# Keep TypeToken and its subclasses (including anonymous inner classes
# created with `object : TypeToken<...>() {}`)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Prevent R8 from stripping away fields accessed reflectively by Gson
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ──────────────────────────────────────────────
# App data class serialized with Gson
# Keep field names so Gson reflection-based (de)serialization works.
# ──────────────────────────────────────────────
-keepclassmembers class com.terasumi.sellerkeyboard.database.Snippets {
    <fields>;
}

# ──────────────────────────────────────────────
# Glide 4.x
# Generated GlideModule / LibraryGlideModules must survive minification.
# ──────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

# ──────────────────────────────────────────────
# exp4j 0.4.x (calculator expression evaluator)
# Library uses reflection to find built-in functions/operators.
# ──────────────────────────────────────────────
-keep class net.objecthunter.exp4j.** { *; }

# ──────────────────────────────────────────────
# Lottie Compose
# Keep OkIO/OkHTTP internals that Lottie may reach via optional deps.
# ──────────────────────────────────────────────
-dontwarn okio.**
-dontwarn okhttp3.**