# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Tell R8 to ignore kxml2 conflicts with Android framework
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**
-dontnote org.xmlpull.v1.**

# Keep xmlpull interfaces but allow obfuscation (treats them as library classes)
-keep,allowobfuscation,allowshrinking interface org.xmlpull.v1.** { *; }

# Suppress the specific warning about Android classes implementing kxml2 interfaces
-dontwarn android.content.res.XmlResourceParser

# For epublib
-keep class nl.siegmann.epublib.** { *; }
-dontwarn nl.siegmann.epublib.**