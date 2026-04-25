# Retrofit + kotlinx.serialization
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class eu.sancris.cititor.**$$serializer { *; }
-keepclassmembers class eu.sancris.cititor.** {
    *** Companion;
}
-keepclasseswithmembers class eu.sancris.cititor.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# CameraX
-keep class androidx.camera.** { *; }
