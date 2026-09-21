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
-ignorewarnings
-keep class com.particlesdevs.photoncamera.circularbarlib.api.*{
    public *;
}
-keep class com.particlesdevs.photoncamera.circularbarlib.control.ManualParamModel{
    public *;
}
-keep class com.particlesdevs.photoncamera.circularbarlib.camera.** {
    *;
}
# The app module references these implementation classes directly (back-press
# observer, shared M3 motion tokens), so they must survive release shrinking
# with their original names.
-keep class com.particlesdevs.photoncamera.circularbarlib.console.** {
    *;
}
-keep class com.particlesdevs.photoncamera.circularbarlib.model.** {
    *;
}
-keep class com.particlesdevs.photoncamera.circularbarlib.util.** {
    *;
}

# The app drives the palette bubble's dome grow/collapse animation and reads
# the drawable's live geometry for the preview blur mask, so these UI classes
# are also referenced from the app module and must keep their names.
-keep class com.particlesdevs.photoncamera.circularbarlib.ui.Binding {
    public *;
}
-keep class com.particlesdevs.photoncamera.circularbarlib.ui.views.ManualPaletteBackground {
    public *;
}
