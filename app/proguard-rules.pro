# Add project specific ProGuard rules here.
-keep class com.minecraft.bedrockserver.** { *; }
-keepclassmembers class * {
    native <methods>;
}
