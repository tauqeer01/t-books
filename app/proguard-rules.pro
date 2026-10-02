# BookFlow release (R8) rules.

# Keep line numbers so Play Console and on-device crash logs show readable stack traces.
# The mapping file is bundled into the AAB, so Play de-obfuscates automatically.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PDFBox-Android: JPEG 2000 support is an optional extra library that BookFlow doesn't bundle.
-dontwarn com.gemalto.jp2.**
