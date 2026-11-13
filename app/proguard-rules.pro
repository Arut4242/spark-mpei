# Keep models for kotlinx.serialization
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepattributes *Annotation*,InnerClasses,EnclosingMethod
