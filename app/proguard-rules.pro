# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.keltruc.mymemos.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.keltruc.mymemos.**$$serializer { *; }
# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# Tink (via EncryptedSharedPreferences) references error-prone annotations that are compile-only.
-dontwarn com.google.errorprone.annotations.**
# Room entities, DTOs and models are only touched through reflection-free code, but keep names
# of serialisable classes so kotlinx.serialization descriptors stay stable.
-keep class com.keltruc.mymemos.network.dto.** { *; }
