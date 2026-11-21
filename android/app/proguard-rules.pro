# 项目专属 R8 / ProGuard 混淆保护规则

# Retrofit、OkHttp 与 Okio
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-dontwarn okio.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Gson 反射序列化保活规则
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.** { *; }

# 6tail 农历历法库（避免 R8 裁切反射或节气查找表）
-keep class com.nlf.calendar.** { *; }

# 领域模型、网络接口、AI 客户端与本地存储模型
-keep class com.zeng.lottery.data.model.** { *; }
-keep class com.zeng.lottery.data.local.** { *; }
-keep class com.zeng.lottery.data.api.** { *; }
-keep class com.zeng.lottery.ai.** { *; }

# ViewModel 反射与 AndroidViewModelFactory 兼容
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
