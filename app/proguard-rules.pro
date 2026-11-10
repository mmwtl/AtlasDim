# R8 full mode (AGP 8 default). There are no runtime dependencies; reflection only reaches
# platform and firmware classes, which are not in the APK, so no extra keep rules are needed.

# Readable stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
