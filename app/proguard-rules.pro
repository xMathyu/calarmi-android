# kotlinx.serialization: los modelos que se guardan como JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.mathyusolutions.calarmi.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mathyusolutions.calarmi.**$$serializer { *; }

# Credential Manager (iniciar sesión con Google): el proveedor de Play Services se carga
# por reflexión. Regla recomendada en la guía de Credential Manager.
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
