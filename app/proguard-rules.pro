# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ExoPlayer classes
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep custom views
-keep public class com.sonnet.player.view.** { *; }
