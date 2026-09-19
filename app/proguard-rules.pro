# Add project specific ProGuard / R8 rules here.

# 1. WebView JavascriptInterface (CRITICAL for Stream Sniffer)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.example.ui.screens.WebAppInterface { *; }

# 2. Room Database & DAOs
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# 3. Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 4. OkHttp & Okio
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 5. Moshi / Kotlin Reflection & Data Models
-keep class com.example.data.model.** { *; }
-keep class com.example.sniffer.model.** { *; }
-keep class com.example.data.entity.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# 6. Coroutines
-dontwarn kotlinx.coroutines.**
