-keepclassmembers @kotlinx.serialization.Serializable class dev.leeeet.wallpapermoe.** {
    *** Companion;
}
-keepclasseswithmembers class dev.leeeet.wallpapermoe.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers,allowobfuscation,allowshrinking class kotlinx.coroutines.flow.FlowKt__LimitKt {
    *** *emitAbort$FlowKt__LimitKt(...);
}
