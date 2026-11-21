# Add project specific ProGuard rules here.

# Retrofit, OkHttp and Okio
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson Serialization Keep Rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }

# Domain, AI, and Local Persistence Models
-keep class com.zeng.lottery.data.model.** { *; }
-keep class com.zeng.lottery.data.local.** { *; }
-keep class com.zeng.lottery.ai.** { *; }

# ViewModel reflection and AndroidViewModelFactory compatibility
-keep class com.zeng.lottery.ui.viewmodel.LotteryViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, ...);
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
    public <init>(android.app.Application, ...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>();
    public <init>(...);
}
