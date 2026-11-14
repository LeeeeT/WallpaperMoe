-keepclassmembers @kotlinx.serialization.Serializable class dev.leeeet.wallpapermoe.** {
    *** Companion;
}
-keepclasseswithmembers class dev.leeeet.wallpapermoe.** {
    kotlinx.serialization.KSerializer serializer(...);
}
