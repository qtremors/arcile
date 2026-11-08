# Arcile release shrinking rules.
#
# Libraries ship their own consumer rules: kotlinx.serialization keeps generated serializers and
# companions, Hilt keeps its components, and Navigation resolves type-safe routes through those
# serializers. Persisted JSON keys and route names come from compile-time serial names, so
# renaming classes does not change them. Only add a rule here for code reached by reflection.

# Readable crash traces without shipping original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Commons Compress exposes optional Zstandard support that Arcile does not ship.
-dontwarn com.github.luben.zstd.ZstdInputStream

# Audio tagging uses ArtworkFactory's Android mode. Its unused desktop decoder
# references Java desktop image classes that are not part of Android.
-dontwarn java.awt.image.BufferedImage
-dontwarn javax.imageio.ImageIO
-dontwarn javax.imageio.stream.ImageInputStream
