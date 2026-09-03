# Daybook keeps its ProGuard/R8 surface deliberately small: there is no
# reflection-based serialisation, no Room, and no network stack, so the default
# android-optimize rules cover almost everything.

# org.json is part of the Android platform, not bundled, but R8 sees the
# references. Keep them quiet.
-dontwarn org.json.**

# Keep line numbers so Play Console crash reports are readable, while still
# obfuscating class names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
