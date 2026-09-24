# R8 / ProGuard rules for release (FOSS + Play).
# Investigated before enabling minify for F-Droid fdroiddata !46596 (linsui).
# Library AARs already ship consumer rules for Room, WorkManager, Glide, Retrofit,
# OkHttp, Gson internals, kotlinx-coroutines, kotlinx-serialization-json, and
# Play Billing. Rules below cover app-owned reflection and the sanitized MapLibre AAR.

# ---------------------------------------------------------------------------
# WebView JavaScript bridges
# JS calls methods by their Java names. Inner/anonymous classes:
# NativeMapHandler.OsrsWikiBridge, PageWebViewManager.RenderTimelineLogger,
# PageWebViewManager.ClipboardBridge, osrsCalculatorApiBridge,
# osrsPreparedArticleWebViewStore.PrewarmBridge, TablePreviewRenderer stub,
# ui.map.WebViewInterface.
# ---------------------------------------------------------------------------
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---------------------------------------------------------------------------
# Gson news-feed cache
# NewsRepository uses Gson.fromJson/toJson on WikiFeed and nested data classes
# (field-name reflection; not kotlinx.serialization).
# ---------------------------------------------------------------------------
-keep class com.omiyawaki.osrswiki.news.model.** { *; }

# ---------------------------------------------------------------------------
# kotlinx.serialization
# App + undergroundmaps use @Serializable DTOs, custom KSerializers
# (DateAsLongSerializer, TextFieldSerializer), and Json.decodeFromString on
# WebView payloads (NativeMapHandler MapRect/MapData, article gesture JSON).
# Library consumer rules cover the runtime; keep generated serializers here.
# ---------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.omiyawaki.osrswiki.**$$serializer { *; }
-keepclassmembers class com.omiyawaki.osrswiki.** {
    *** Companion;
}
-keepclasseswithmembers class com.omiyawaki.osrswiki.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# MapLibre Native (org.maplibre.gl:android-sdk:11.12.1, sanitized local AAR)
# File-based AAR consumer-proguard application is unreliable, so duplicate the
# upstream 11.12.1 rules plus JNI nativePtr/native-method keeps from
# maplibre-native#3627. GeoJSON uses Gson field reflection.
# ---------------------------------------------------------------------------
-keepattributes Signature, *Annotation*, EnclosingMethod
-keep class com.google.gson.JsonArray { *; }
-keep class com.google.gson.JsonElement { *; }
-keep class com.google.gson.JsonObject { *; }
-keep class com.google.gson.JsonPrimitive { *; }
-dontnote com.google.gson.**
-keep enum org.maplibre.android.tile.TileOperation
-keep class org.maplibre.android.maps.RenderingStats { *; }
-keep class org.maplibre.android.maps.NativeMapOptions { *; }
-keep class org.maplibre.geojson.** { *; }
-dontwarn com.google.auto.value.**
-keepclasseswithmembernames class org.maplibre.** {
    native <methods>;
}
-keepclassmembers class org.maplibre.** {
    long nativePtr;
}

# Crash traces remain useful after minify.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
