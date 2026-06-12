# =========================================================================
# Watch with Friends — Release (R8) kuralları
# =========================================================================

# Hata ayıklama için kaynak/satır bilgisini koru (Play Console crash okunaklı olsun)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Firestore reflection için anotasyon/imza bilgisi
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations

# -------------------------------------------------------------------------
# Firestore veri modelleri — KRİTİK
# Firestore toObject()/set() reflection ile alan adlarını kullanır; bu sınıflar
# ve üyeleri obfuscate/strip EDİLMEMELİ, yoksa veri okuma/yazma bozulur.
# -------------------------------------------------------------------------
-keep class com.watch.watchtofriend.data.model.** { *; }
-keepclassmembers class com.watch.watchtofriend.data.model.** {
    <init>();
    <fields>;
    <methods>;
}

# -------------------------------------------------------------------------
# WebView JavaScript köprüsü (WatchBridge) — @JavascriptInterface metotları
# obfuscate edilirse JS'ten erişilemez → senkron/oynatma bozulur.
# -------------------------------------------------------------------------
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# -------------------------------------------------------------------------
# Firebase / Google — kütüphaneler kendi tüketici kurallarını sağlar; ek güvenlik
# -------------------------------------------------------------------------
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# FcmService — FirebaseMessagingService alt sınıfı; silinmemeli
-keep class com.watch.watchtofriend.FcmService { *; }

# -------------------------------------------------------------------------
# WorkManager — Worker alt sınıfları yansıma ile başlatılır; obfuscate
# edilirse ClassNotFoundException fırlatır ve arka plan bildirimleri durur.
# -------------------------------------------------------------------------
-keep class com.watch.watchtofriend.NotificationWorker { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# -------------------------------------------------------------------------
# Coil 3 — ImageLoader yansıma ile bileşenleri keşfeder; R8 bunları
# silebilir, bu durumda AsyncImage yüklenemez ve beyaz kare gösterilir.
# -------------------------------------------------------------------------
-keep class coil3.** { *; }
-dontwarn coil3.**

# -------------------------------------------------------------------------
# WebRTC (libwebrtc) — native JNI köprüsü; obfuscate edilirse native
# çağrılar çözümlenemez → P2P bağlantısı tamamen bozulur.
# -------------------------------------------------------------------------
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings { <fields>; }
