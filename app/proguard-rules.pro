# kotlinx.serialization ships consumer rules, but the generated serializers on
# @Serializable types here are referenced only reflectively.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
