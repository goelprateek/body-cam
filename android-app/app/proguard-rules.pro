# LiveKit & WebRTC (Crucial for streaming)
-keep class io.livekit.android.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-dontwarn io.livekit.android.**

# ViewBinding (Sometimes stripped in release)
-keep class com.kriyanshtech.bodycam.databinding.** { *; }

# Retrofit & OkHttp
-keepattributes Signature, InnerClasses, AnnotationDefault
-keep public class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowoptimization interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Your Data & Model classes
-keep class com.kriyanshtech.bodycam.** { *; }
-keepnames class com.kriyanshtech.bodycam.** { *; }

# AndroidX Lifecycle
-keep class androidx.lifecycle.** { *; }
