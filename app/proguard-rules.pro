-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Parsed through Android's built-in org.json APIs. Keep update DTO names for
# readable crash reports even though no reflection is used.
-keep class ru.yavasilek.netpulse.update.** { *; }
