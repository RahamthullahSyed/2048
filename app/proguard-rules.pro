# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# WebView JavaScript Interface protection
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the specific interface class
-keep class com.play.puzzle2048.MainActivity$WebAppInterface {
    public *;
}

# WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Play Services Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Play Asset Delivery / App Update
-keep class com.google.android.play.core.** { *; }
-dontwarn com.google.android.play.core.**
