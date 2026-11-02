# Add project specific ProGuard rules here.

# Retrofit, OkHttp and Okio
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Gson Serialization Keep Rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Domain and AI Models
-keep class com.zeng.lottery.data.model.** { *; }
-keep class com.zeng.lottery.ai.AiConfig { *; }
-keep class com.zeng.lottery.ai.AiProvider { *; }
-keep class com.zeng.lottery.ai.KeyStoreManager { *; }
