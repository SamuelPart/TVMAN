# Add project-specific ProGuard rules here.
# By default, the rules in this file are appended to the default ProGuard rules.
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Glide rules
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# ── TVMAN: protecciones para R8 ──────────────────────────────────────
# LibVLC: los metodos nativos JNI se llaman por nombre desde C — si R8
# los renombra, el video no abre.
-keep class org.videolan.libvlc.** { *; }
-keep class org.videolan.medialibrary.** { *; }

# Puente JS del reproductor web (BOT): addJavascriptInterface llama a los
# metodos ANOTADOS por nombre desde JavaScript — no se pueden ofuscar.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Modelos Serializable que viajan entre Activities via Intent extras.
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
}
-keep class com.tvman.app.CineMedia { *; }
-keep class com.tvman.app.Channel { *; }
-keep class com.tvman.app.Episode { *; }
-keep class com.tvman.app.CastMember { *; }

# Enumeraciones accedidas por valueOf en el parser/enrutador.
-keepclassmembers enum com.tvman.app.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
