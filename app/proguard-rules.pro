# kotlinx.serialization: los modelos que se guardan como JSON.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class com.mathyusolutions.calarmi.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.mathyusolutions.calarmi.**$$serializer { *; }
