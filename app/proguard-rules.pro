# kotlinx.serialization keeps generated serializers for our DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class dev.opencode.mobile.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class dev.opencode.mobile.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit barcode scanning is loaded reflectively by the Play Services bridge;
# R8 cannot see those references and would strip the model metadata.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-dontwarn com.google.mlkit.**
