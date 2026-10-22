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

# Coil 3 resolves its fetchers and decoders from a service list at runtime, and
# picks a network client by the platform it finds. R8 cannot see either.
-keep class coil3.** { *; }
-dontwarn coil3.**

# FCM instantiates the service and its token listeners reflectively from the
# manifest and the FirebaseApp registry, so R8 cannot see the references.
-keep class dev.opencode.mobile.core.push.PushService { *; }
-keep class com.google.firebase.messaging.** { *; }
-dontwarn com.google.firebase.**
-keepattributes Signature
