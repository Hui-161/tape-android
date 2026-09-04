# Add project specific ProGuard rules here.
# For more details, see https://developer.android.com/build/shrink-code
-dontwarn org.jetbrains.annotations.**

# ARCore — keep all public API and JNI surface
-keep class com.google.ar.** { *; }
-keep class com.google.ar.core.** { *; }
-dontwarn com.google.ar.**

# SceneView / ARSceneView
-keep class io.github.sceneview.** { *; }
-dontwarn io.github.sceneview.**

# Room — keep entity and DAO classes for reflection
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# DataStore — keep Keys (Preferences.Key<*>) that Room-like processing needs
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences { *; }

# Hilt — generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ActivityComponentManager { *; }