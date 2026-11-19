# ==============================================================================
# Règles ProGuard / R8 pour Vroum (release)
# ==============================================================================

# Conserver les lignes pour diagnostiquer les crashs sans exposer de noms inutiles
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ------------------------------------------------------------------------------
# Room Database & SQLite
# ------------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**
-keepclassmembers class com.vroum.app.data.local.** { *; }

# ------------------------------------------------------------------------------
# OkHttp & Okio
# ------------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ------------------------------------------------------------------------------
# Kotlin Coroutines
# ------------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.flow.**

# ------------------------------------------------------------------------------
# Modèles & BuildConfig
# ------------------------------------------------------------------------------
-keep class com.vroum.app.BuildConfig { *; }
-keep class com.vroum.app.model.** { *; }

# ------------------------------------------------------------------------------
# Supprimer les logs debug en version release
# ------------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
