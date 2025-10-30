# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Service and Activity classes
-keep class com.screenkeeper.MainActivity { *; }
-keep class com.screenkeeper.MainFragment { *; }
-keep class com.screenkeeper.LogsFragment { *; }
-keep class com.screenkeeper.ViewPagerAdapter { *; }
-keep class com.screenkeeper.ScreenUpdateService { *; }
-keep class com.screenkeeper.LogManager { *; }
-keep class com.screenkeeper.DebugLogger { *; }

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Optimization is safe for this app
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
