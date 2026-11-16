# kotlinx.serialization ships its own rules; keep W Design models as a safety net because specs are
# stored as JSON on disk and inside .wdesign/.wdbackup files.
-keep,includedescriptorclasses class com.wdesign.core.model.**$$serializer { *; }
-keepclassmembers class com.wdesign.core.model.** { *** Companion; }
-keepclasseswithmembers class com.wdesign.core.model.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.wdesign.core.format.WManifest { *; }
-keep class com.wdesign.core.format.BackupManifest { *; }
-keepattributes *Annotation*, InnerClasses, Signature
# javax.xml parser lookup used for MTZ description/theme_values parsing
-dontwarn javax.xml.**
-dontwarn org.w3c.dom.**
